import json
with open('lang/en.json') as f:
    data = json.load(f)
root = data.get('pf2e-kingmaker-tools', {})

# Print all kingdom keys
kingdom = root.get('kingdom', {})
for k, v in sorted(kingdom.items()):
    if isinstance(v, dict):
        print(f'kingdom.{k} = <nested>')
        for k2, v2 in sorted(v.items()):
            print(f'  kingdom.{k}.{k2} = {v2}')
    else:
        print(f'kingdom.{k} = {v}')

print()
print('--- chatMessages ---')
chat = root.get('chatMessages', {})
for k, v in sorted(chat.items()):
    if isinstance(v, dict):
        print(f'chatMessages.{k} = <nested>')
        for k2, v2 in sorted(v.items()):
            print(f'  chatMessages.{k}.{k2} = {v2}')
    else:
        print(f'chatMessages.{k} = {v}')
