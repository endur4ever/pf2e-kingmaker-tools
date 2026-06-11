import json
with open('lang/en.json') as f:
    d = json.load(f)
ns = d['pf2e-kingmaker-tools']
print(json.dumps(ns.get('kingdom', {}), indent=2))
