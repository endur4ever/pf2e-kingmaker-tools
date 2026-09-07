package at.posselt.pfrpg2e.fixtures

/**
 * The minimum Foundry globals a jsMain camping file needs before it can be loaded in a test.
 *
 * Kotlin/JS runs a file's top-level property initializers the first time anything in that file is
 * touched, and those initializers reach for `CONFIG`. Without it a test does not fail on its
 * assertion -- it fails with `ReferenceError: CONFIG is not defined` before the code under test
 * runs at all, which is a large part of why the adapter layer had no tests.
 *
 * Deliberately does NOT install `game` or `ui`. An earlier version did, and a `game.user.isGM`
 * defaulting to true silently broke TurnWizardApplicationTest, which builds its own game and
 * asserts the opposite. Globals are shared by the whole run, so a fixture that installs more than
 * it must is a fixture that breaks tests it never heard of: anything needing `game` should build
 * its own, as the existing tests do.
 *
 * These are empty shells, not an imitation of Foundry. Anything needing real document behaviour is
 * tested through the update payload it produces instead -- Foundry interprets deletion markers
 * inside its own update pipeline, and a hand-rolled stand-in would be a fixture that can pass
 * while production fails.
 */
fun installFoundryGlobals() {
    js(
        """
        if (typeof globalThis._del === 'undefined') { globalThis._del = { __forcedDeletion: true }; }
        if (typeof globalThis.flattenObject === 'undefined') {
            globalThis.flattenObject = function(obj, _d) {
                _d = _d || 0;
                if (_d > 100) return {};
                var flat = {};
                for (var k in (obj || {})) {
                    if (Object.prototype.hasOwnProperty.call(obj, k)) {
                        var v = obj[k];
                        if (v && typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length > 0) {
                            var nested = globalThis.flattenObject(v, _d + 1);
                            for (var nk in nested) {
                                flat[k + '.' + nk] = nested[nk];
                            }
                        } else {
                            flat[k] = v;
                        }
                    }
                }
                return flat;
            };
        }
        if (typeof globalThis.CONFIG === 'undefined') { globalThis.CONFIG = {}; }
        if (typeof globalThis.CONFIG.PF2E === 'undefined') {
            function MockDoc() {}
            if (typeof globalThis.foundry !== 'undefined' && globalThis.foundry.abstract && globalThis.foundry.abstract.Document) {
                MockDoc.prototype = Object.create(globalThis.foundry.abstract.Document.prototype);
            }
            globalThis.CONFIG.PF2E = {
                Actor: {
                    documentClasses: {
                        character: MockDoc,
                        npc: MockDoc,
                        party: MockDoc,
                        hazard: MockDoc,
                        loot: MockDoc,
                        vehicle: MockDoc,
                        familiar: MockDoc,
                        army: MockDoc
                    }
                },
                Item: {
                    documentClasses: {
                        action: MockDoc,
                        affliction: MockDoc,
                        armor: MockDoc,
                        backpack: MockDoc,
                        campaignFeature: MockDoc,
                        condition: MockDoc,
                        consumable: MockDoc,
                        effect: MockDoc,
                        equipment: MockDoc,
                        feat: MockDoc,
                        shield: MockDoc,
                        treasure: MockDoc,
                        weapon: MockDoc
                    }
                }
            };
        }
        if (typeof globalThis.CONFIG.Actor === 'undefined') {
            globalThis.CONFIG.Actor = { documentClass: null, documentClasses: {} };
        }
        if (typeof globalThis.CONFIG.Item === 'undefined') {
            globalThis.CONFIG.Item = { documentClass: null, documentClasses: {} };
        }
        """
    )
}
