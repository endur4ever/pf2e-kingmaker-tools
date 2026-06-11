import json
with open('lang/en.json') as f:
    data = json.load(f)
root = data.get('pf2e-kingmaker-tools', {})
def find_keys(obj, prefix='', depth=0):
    results = []
    if isinstance(obj, dict):
        for k, v in obj.items():
            full = f'{prefix}.{k}' if prefix else k
            if any(term in k.lower() for term in ['rptoxp', 'maximfam', 'endturn', 'turnwizard', 'autogain', 'xpaward', 'famewizard', 'changefame', 'changerp']):
                results.append((full, v if not isinstance(v, dict) else '<nested>'))
            if isinstance(v, dict):
                results.extend(find_keys(v, full, depth+1))
    return results
matches = find_keys(root)
for path, val in matches:
    print(f'{path} = {val}')
