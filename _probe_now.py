import re, urllib.request

UA_BOT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
UA_FB = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
UA_MOBILE = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

def fetch(url, ua, timeout=18):
    req = urllib.request.Request(url, headers={
        "User-Agent": ua,
        "Accept": "text/html,application/xhtml+xml,application/json",
        "Accept-Language": "en-US,en;q=0.9",
        "Cookie": "locale=en_US",
    })
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            data = r.read(900_000)
            return r.status, data.decode("utf-8", "ignore")
    except Exception as e:
        return str(e)[:180], ""

def hits(text):
    plain = re.sub(r"<[^>]{0,200}>", " ", text)
    plain = plain.replace("&nbsp;", " ").replace("&amp;", "&")
    found = re.findall(r"(\d[\d,\.]{0,14}\s*[KMBkmb]?)\s+followers?", plain, re.I)
    following = re.findall(r"(\d[\d,\.]{0,14}\s*[KMBkmb]?)\s+following", plain, re.I)
    likes = re.findall(r"(\d[\d,\.]{0,14}\s*[KMBkmb]?)\s+(?:people like|likes)", plain, re.I)
    return found[:3], following[:2], likes[:2]

tests = [
    ("jina-in", "https://r.jina.ai/http://www.linkedin.com/in/williamhgates", UA_MOBILE),
    ("jina-plugin", "https://r.jina.ai/http://www.facebook.com/plugins/page.php?href=https%3A%2F%2Fwww.facebook.com%2FNASA", UA_MOBILE),
    ("plugin-mobile", "https://www.facebook.com/plugins/page.php?href=https%3A%2F%2Fwww.facebook.com%2FNASA&locale=en_US", UA_MOBILE),
]

for name, url, ua in tests:
    status, body = fetch(url, ua)
    f, g, likes = hits(body)
    print(f"{name} status={status} bytes={len(body)} followers={f} following={g} likes={likes}")
