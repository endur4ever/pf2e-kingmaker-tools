import json
import sys

def reject_duplicate_keys(pairs):
    seen = {}
    for key, value in pairs:
        if key in seen:
            print(f'DUPLICATE KEY: {key}', file=sys.stderr)
            print(f'  First value: {seen[key]}', file=sys.stderr)
            print(f'  Second value: {value}', file=sys.stderr)
            sys.exit(1)
        seen[key] = value
    return seen

with open('/home/grego/code/pf2e-kingmaker-tools/lang/en.json', encoding='utf-8') as f:
    data = json.load(f, object_pairs_hook=reject_duplicate_keys)
print('JSON parsed successfully')