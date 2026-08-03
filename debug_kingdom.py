import json

with open('/home/grego/code/pf2e-kingmaker-tools/lang/en.json') as f:
    data = json.load(f)

kingdom = data['pf2e-kingmaker-tools']['kingdom']
print('Keys in kingdom:')
for k in kingdom.keys():
    print(f'  {k}')

# Check for 'turn' key
if 'turn' in kingdom:
    print(f'\nkingdom.turn = {json.dumps(kingdom["turn"], indent=2)}')