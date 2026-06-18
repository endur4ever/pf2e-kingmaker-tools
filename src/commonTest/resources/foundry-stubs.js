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
            },
            DocumentSheetV2: class {
            }
        }
    }
}