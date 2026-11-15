# ينزّل خلفيات من Unsplash (رخصة Unsplash) ويحفظ اسم المصوّر لكل صورة.
import json, sys, urllib.request, urllib.parse, os
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
Q = [("night_road", "night highway light trails"), ("nebula", "purple nebula space"), ("city_night", "city skyline night lights"),
     ("desert_road", "desert road sunset"), ("neon", "neon lights street night"), ("forest", "dark foggy forest"),
     ("ocean_night", "ocean night moon"), ("euphrates", "river sunset palm trees")]
UA = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) MyCarBot/1.0"}
credits = {}
for key, q in Q:
    url = "https://unsplash.com/napi/search/photos?" + urllib.parse.urlencode({"query": q, "per_page": 20, "orientation": "landscape"})
    try:
        data = json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30))
    except Exception as e:
        print("SEARCH FAIL", key, e); continue
    picks = []
    for r in data.get("results", []):
        if r.get("premium") or r.get("plus") or (r.get("sponsorship") is not None): continue
        if r.get("width", 0) < r.get("height", 1) * 1.4: continue
        picks.append(r)
        if len(picks) >= 4: break
    for i, r in enumerate(picks):
        raw = r["urls"]["raw"] + "&w=1600&q=82&fm=jpg&fit=crop&ar=16:9"
        fn = f"{key}_{i}.jpg"
        try:
            urllib.request.urlretrieve(raw, os.path.join(out, fn))
            credits[fn] = {"id": r["id"], "name": r["user"]["name"], "user": r["user"]["username"], "page": r["links"]["html"], "desc": r.get("alt_description")}
            print("GOT", fn, r["user"]["name"])
        except Exception as e:
            print("DL FAIL", fn, e)
json.dump(credits, open(os.path.join(out, "credits.json"), "w"), ensure_ascii=False, indent=1)
