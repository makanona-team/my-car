# ينزّل خلفيات Unsplash المحددة (رخصة Unsplash) بدقة 1600 ويحفظ صفحة كل صورة واسم المصوّر إن أمكن.
import json, sys, urllib.request, re, os
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
IDS = {
 "night_road": ["V862kywlKkw", "Lr201W3WbTE", "ewPvwRx7ub8"],
 "nebula": ["rTZW4f02zY8", "OOBtVJrDZUw"],
 "city_night": ["qQwxU7NJWTI", "DxHR8K5Egjk", "nxCtO8W9JLo"],
 "desert_road": ["1cD6Dm7VcgA", "lTdsSV4jn64", "LgHNehAIcOs"],
 "neon": ["CPAajYWQeR4", "YNLIep6Pn4g", "w45DzhCO_cE"],
 "forest": ["QE3BkF3ygp0", "oAtoPUplYHU", "ihryD68bBc0"],
 "ocean_night": ["N_NFolZUgZY", "Jvuvr8nd2lM", "URLlQPnfG6Y"],
 "palmyra": ["AA2FRH0NU3k", "vi1fIqmbHP0"],
}
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
      "Accept": "text/html,application/xhtml+xml,image/avif,image/webp,*/*", "Accept-Language": "en-US,en;q=0.9"}
def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=40)
credits = {}
for key, ids in IDS.items():
    for pid in ids:
        fn = f"{key}_{pid}.jpg"; info = {"id": pid, "page": f"https://unsplash.com/photos/{pid}"}
        img = None
        try:
            r = get(f"https://unsplash.com/photos/{pid}/download?force=true&w=1920")
            data = r.read()
            if data[:3] == b"\xff\xd8\xff": img = data; info["via"] = "download"
        except Exception as e:
            info["dl_err"] = str(e)
        try:
            html = get(info["page"]).read().decode("utf-8", "replace")
            m = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', html)
            t = re.search(r'<title>([^<]+)</title>', html)
            a = re.search(r'Photo by ([^<"]+?) on Unsplash', html)
            info["title"] = t.group(1) if t else None
            info["author"] = a.group(1) if a else None
            if img is None and m:
                u = m.group(1).replace("&amp;", "&")
                u = re.sub(r'([?&])(w|h|fit|crop|q|fm|auto|ixlib|mark\w*|blend\w*)=[^&]*', r'\1', u)
                u = u + ("&" if "?" in u else "?") + "w=1920&q=85&fm=jpg"
                img = get(u).read(); info["via"] = "og"
        except Exception as e:
            info["page_err"] = str(e)
        if img:
            open(os.path.join(out, fn), "wb").write(img); info["file"] = fn
        print(fn, {k: v for k, v in info.items() if k != "page"})
        credits[fn] = info
json.dump(credits, open(os.path.join(out, "credits.json"), "w"), ensure_ascii=False, indent=1)
