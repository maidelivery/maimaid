import urllib.request
import json
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

url = 'https://www.diving-fish.com/api/maimaidxprober/query/player'
req = urllib.request.Request(url, headers={'Content-Type': 'application/json'})

data = json.dumps({
    'qq': '1842490165',
    'b50': True
}).encode('utf-8')

try:
    with urllib.request.urlopen(req, data=data, context=ctx) as r:
        j = json.loads(r.read().decode())
        if 'charts' in j:
            charts = j['charts']
            print("Type of charts:", type(charts))
            print("Length of charts", len(charts))
            if isinstance(charts, dict):
                print("Keys:", charts.keys())
                for k in charts:
                    print(f" {k} length: {len(charts[k])}")
                    if len(charts[k]) > 0:
                        print(json.dumps(charts[k][0], indent=2, ensure_ascii=False))
            elif isinstance(charts, list) and len(charts) > 0:
                print(json.dumps(charts[0], indent=2, ensure_ascii=False))
except Exception as e:
    print("Error:", e)
