class Hooks {
    static on(key, cb) {}
    static once(key, cb) {}
    static off(key, cb) {}
    static callAll(key, ...args) {}
    static call(key, ...args) {}
}
globalThis.Hooks = Hooks;

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
        ChatMessage: class {
            static create(data) {
                if (globalThis.ChatMessage && globalThis.ChatMessage !== this && globalThis.ChatMessage.create) {
                    return globalThis.ChatMessage.create(data);
                }
                return Promise.resolve(data);
            }
            static getSpeaker(opts) {
                if (globalThis.ChatMessage && globalThis.ChatMessage !== this && globalThis.ChatMessage.getSpeaker) {
                    return globalThis.ChatMessage.getSpeaker(opts);
                }
                return {};
            }
            static applyMode(data, mode) {
                if (globalThis.ChatMessage && globalThis.ChatMessage !== this && globalThis.ChatMessage.applyMode) {
                    return globalThis.ChatMessage.applyMode(data, mode);
                }
                data.mode = mode;
            }
        },
        Combat: class {},
        TokenDocument: class {},
        RollTable: class {},
        JournalEntryPage: class {},
    },
    data: {
        fields: {}
    },
    helpers: {
        Hooks: Hooks
    },
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
        handlebars: {
            renderTemplate: function(path, data) {
                if (globalThis.renderTemplateMock) {
                    return globalThis.renderTemplateMock(path, data);
                }
                return Promise.resolve('<template data-path="' + path + '"></template>');
            },
            loadTemplates: function() {
                return Promise.resolve([]);
            }
        },
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