# ينزّل خلفيات Unsplash (رخصة Unsplash) من شبكة الصور مباشرة، بدقة 1920، مع اسم المصوّر.
import json, sys, urllib.request, os
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
W = {
 "night_road":  ("photo-1532463788086-56a492a0b34a", "David Becker", "V862kywlKkw"),
 "nebula":      ("photo-1462331940025-496dfbfc7564", "NASA", "rTZW4f02zY8"),
 "city_night":  ("photo-1443175679639-685643a7fd62", "TEDDY2 H", "qQwxU7NJWTI"),
 "desert_road": ("photo-1639402477646-e88e811eef21", "Andrew Svk", "1cD6Dm7VcgA"),
 "desert_road2":("photo-1623284161455-26b238c3946d", "Dane Deaner", "lTdsSV4jn64"),
 "neon":        ("photo-1701338462908-6022ef7cb466", "Darwin Vegher", "CPAajYWQeR4"),
 "forest":      ("photo-1507149677524-254e3ebb240f", "Jakub Kriz", "QE3BkF3ygp0"),
 "ocean_night": ("photo-1568233823082-873ab0156ad7", "Patrick Schöpflin", "N_NFolZUgZY"),
 "palmyra":     ("photo-1727360928420-62a4d5dd9c0f", "Wietse Jongsma", "AA2FRH0NU3k"),
}
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"}
credits = {}
for key, (photo, name, pid) in W.items():
    url = f"https://images.unsplash.com/{photo}?w=1920&h=1080&fit=crop&crop=entropy&q=82&fm=jpg"
    try:
        data = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60).read()
        open(os.path.join(out, key + ".jpg"), "wb").write(data)
        credits[key] = {"author": name, "page": f"https://unsplash.com/photos/{pid}", "bytes": len(data)}
        print("GOT", key, len(data))
    except Exception as e:
        print("FAIL", key, e)
json.dump(credits, open(os.path.join(out, "credits.json"), "w"), ensure_ascii=False, indent=1)
