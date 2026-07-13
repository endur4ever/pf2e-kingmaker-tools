import json

with open('/home/grego/code/pf2e-kingmaker-tools/lang/en.json', 'r', encoding='utf-8') as f:
    data = json.load(f)

ns = data['pf2e-kingmaker-tools']

new_entry = {
    "improve-settlement": {
        "title": "Improve Settlement",
        "description": "<p>You send your best workers to improve one of your non-capital settlements to the next highest settlement type (kingdom level restrictions still apply). Spend the following amount of resources based on the settlement type you want to upgrade to:</p><ul><li><b>Town</b>: 5 Stone, 5 Ore, 5 Lumber, 1 Luxury, 10 RP</li><li><b>City</b>: 10 Stone, 10 Ore, 10 Lumber, 3 Luxuries, 25 RP</li><li><b>Metropolis</b>: 20 Stone, 20 Ore, 20 Lumber, 5 Luxuries, 50 RP</li></ul><p>Then place the following structures in the settlement (and enough houses to prevent being overcrowded):</p><ul><li><b>Town</b>: Bridge (if it has 4 Water Borders), 4 Wooden Walls, General Store, Lumberyard, Granary</li><li><b>City</b>: 4 Stone Walls, Stockyard, Stonemason, Magic Shop</li><li><b>Metropolis</b>: Foundry, Secure Warehouse, Sewer System, Occult Shop</li></ul><p>Use the following settlement levels when trying to buy items:</p><ul><li><b>Town</b>: 3</li><li><b>City</b>: 9</li><li><b>Metropolis</b>: 15</li></ul><p>Attempt an Engineering check.</p>",
        "requirement": "activities.improve-settlement.requirement",
        "criticalSuccess": {
            "msg": "<p>The settlement is upgraded to the next tier. The required commodities and RP are spent. The settlement gains the listed structures and its purchase level increases.</p>"
        },
        "success": {
            "msg": "<p>The settlement is upgraded to the next tier. The required commodities and RP are spent. The settlement gains the listed structures and its purchase level increases.</p>"
        },
        "failure": {
            "msg": "<p>The upgrade effort falls short. No commodities or RP are spent, and the settlement remains at its current tier.</p>"
        },
        "criticalFailure": {
            "msg": "<p>The project is a disaster. You lose half the required commodities (rounded down) and all RP spent. The settlement gains 1 Unrest and remains at its current tier.</p>"
        }
    }
}

# Insert after improve-lifestyle
keys = list(ns.keys())
idx = keys.index('improve-lifestyle')
new_keys = keys[:idx+1] + ['improve-settlement'] + keys[idx+1:]

new_ns = {}
for k in new_keys:
    if k == 'improve-settlement':
        new_ns[k] = new_entry[k]
    else:
        new_ns[k] = ns[k]

data['pf2e-kingmaker-tools'] = new_ns

with open('/home/grego/code/pf2e-kingmaker-tools/lang/en.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=2)

print("Done")