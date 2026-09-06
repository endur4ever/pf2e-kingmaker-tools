class Hooks {
    static on(key) {
    }
}

const _del = {}

const foundry = {
    ux: {},
    abstract: {
        DataModel: class {

        },
        Document: class {

        }
    },
    utils: {
        expandObject: () => {
        },
        deepClone: function(x) {
            return (x == null) ? x : JSON.parse(JSON.stringify(x));
        },
        fromUuid: function(uuid) {
            if (globalThis.fromUuidMock) {
                return globalThis.fromUuidMock(uuid);
            }
            return Promise.resolve(null);
        }
    },
    documents: {

    },
    data: {
        fields: {}
    },
    helpers: {},
    applications: {
        sidebar: {
            ActorDirectory: class {}
        },
        ui: {
            Hotbar: class {}
        },
        ux: {
            TextEditor: {
                implementation: class {
                }
            }
        },
        handlebars: {},
        api: {
            HandlebarsApplicationMixin: (klass) => {
                return class extends klass {
                }
            },
            ApplicationV2: class {
                render() { return Promise.resolve(this); }
                close() { return Promise.resolve(); }
            },
            DocumentSheetV2: class {
                render() { return Promise.resolve(this); }
                close() { return Promise.resolve(); }
            }
        }
    }
}

var CONFIG = globalThis.CONFIG = {
    PF2E: {
        Actor: {
            documentClasses: {
                character: class MockCharacter extends foundry.abstract.Document {},
                npc: class MockNpc extends foundry.abstract.Document {},
                party: class MockParty extends foundry.abstract.Document {},
                hazard: class MockHazard extends foundry.abstract.Document {},
                loot: class MockLoot extends foundry.abstract.Document {},
                vehicle: class MockVehicle extends foundry.abstract.Document {},
                familiar: class MockFamiliar extends foundry.abstract.Document {},
                army: class MockArmy extends foundry.abstract.Document {},
            }
        },
        Item: {
            documentClasses: {
                action: class MockAction extends foundry.abstract.Document {},
                affliction: class MockAffliction extends foundry.abstract.Document {},
                armor: class MockArmor extends foundry.abstract.Document {},
                backpack: class MockBackpack extends foundry.abstract.Document {},
                campaignFeature: class MockCampaignFeature extends foundry.abstract.Document {},
                condition: class MockCondition extends foundry.abstract.Document {},
                consumable: class MockConsumable extends foundry.abstract.Document {},
                effect: class MockEffect extends foundry.abstract.Document {},
                equipment: class MockEquipment extends foundry.abstract.Document {},
                feat: class MockFeat extends foundry.abstract.Document {},
                shield: class MockShield extends foundry.abstract.Document {},
                treasure: class MockTreasure extends foundry.abstract.Document {},
                weapon: class MockWeapon extends foundry.abstract.Document {},
            }
        }
    }
}