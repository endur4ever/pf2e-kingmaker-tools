import json
with open('lang/en.json') as f:
    d = json.load(f)
ns = d['pf2e-kingmaker-tools']
k = ns.get(' kingdom', {})
ct = ns.get('chatMessages', {}).get('endTurn', {})
print("=== kingdom keys ===")
for key in ['xpAwarded', 'fameNow', 'autoGainFamePerTurn', 'rpToXpConversionRate', 'rpToXpConversionLimit', 'maximumFamePoints']:
    val = k.get(key, 'MISSING')
    print(f"  kingdom.{key}: {val}")
print("=== chatMessages.endTurn keys ===")
for key in ['title', 'resetRP', 'reduceMods', 'resetSolutions', 'moveValues']:
    val = ct.get(key, 'MISSING')
    print(f"  chatMessages.endTurn.{key}: {val}")
