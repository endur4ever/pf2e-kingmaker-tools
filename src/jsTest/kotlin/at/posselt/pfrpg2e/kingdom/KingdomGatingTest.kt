package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import com.foundryvtt.core.Game
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

class KingdomGatingTest {
    private fun runTest(block: suspend () -> Unit): dynamic = @Suppress("DELICATE_API_TRANSITIONAL_MINI_MARKER") GlobalScope.promise { block() }

    @Test
    fun testGetOwnedLeaderRolesForGM() = runTest {
        val game: Game = js("""{
            user: { isGM: true }
        }""")
        val kingdom: KingdomData = js("""{
            leaders: {
                ruler: { uuid: "Actor.1" },
                counselor: { uuid: "Actor.2" },
                emissary: { uuid: "Actor.3" },
                general: { uuid: "Actor.4" },
                magister: { uuid: "Actor.5" },
                treasurer: { uuid: "Actor.6" },
                viceroy: { uuid: "Actor.7" },
                warden: { uuid: "Actor.8" }
            }
        }""")
        val owned = getOwnedLeaderRoles(game, kingdom)
        assertEquals(Leader.entries.toSet(), owned)
    }

    @Test
    fun testGetOwnedLeaderRolesForPlayer() = runTest {
        // Setup CONFIG and foundry globals for class checks and fromUuid using ES5 syntax
        js("""
            globalThis.CONFIG = {
                PF2E: {
                    Actor: {
                        documentClasses: {
                            character: function MockCharacter() {},
                            npc: function MockNpc() {}
                        }
                    }
                }
            };
            globalThis.CONFIG.PF2E.Actor.documentClasses.character.prototype = Object.create(foundry.abstract.Document.prototype);
            globalThis.CONFIG.PF2E.Actor.documentClasses.npc.prototype = Object.create(foundry.abstract.Document.prototype);
            
            globalThis.fromUuidMock = function(uuid) {
                if (uuid === "Actor.1" || uuid === "Actor.3") {
                    var actor = new globalThis.CONFIG.PF2E.Actor.documentClasses.character();
                    actor.isOwner = true;
                    return Promise.resolve(actor);
                }
                var otherActor = new globalThis.CONFIG.PF2E.Actor.documentClasses.character();
                otherActor.isOwner = false;
                return Promise.resolve(otherActor);
            };
        """)

        val game: Game = js("""{
            user: { isGM: false }
        }""")
        val kingdom: KingdomData = js("""{
            leaders: {
                ruler: { uuid: "Actor.1" },
                counselor: { uuid: "Actor.2" },
                emissary: { uuid: "Actor.3" },
                general: { uuid: null },
                magister: { uuid: null },
                treasurer: { uuid: null },
                viceroy: { uuid: null },
                warden: { uuid: null }
            }
        }""")
        val owned = getOwnedLeaderRoles(game, kingdom)
        assertEquals(setOf(Leader.RULER, Leader.EMISSARY), owned)
    }
}
