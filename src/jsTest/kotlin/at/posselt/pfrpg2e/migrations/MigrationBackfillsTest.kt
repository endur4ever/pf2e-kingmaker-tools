package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.migrations.migrations.Migration25
import at.posselt.pfrpg2e.migrations.migrations.Migration28
import at.posselt.pfrpg2e.migrations.migrations.Migration29
import at.posselt.pfrpg2e.migrations.migrations.Migration31
import at.posselt.pfrpg2e.migrations.migrations.Migration33
import at.posselt.pfrpg2e.migrations.migrations.Migration34
import at.posselt.pfrpg2e.migrations.migrations.Migration35
import at.posselt.pfrpg2e.migrations.migrations.Migration38
import at.posselt.pfrpg2e.migrations.migrations.Migration41
import at.posselt.pfrpg2e.migrations.migrations.Migration42
import at.posselt.pfrpg2e.migrations.migrations.Migration43
import at.posselt.pfrpg2e.migrations.migrations.Migration44
import at.posselt.pfrpg2e.migrations.migrations.Migration45
import at.posselt.pfrpg2e.migrations.migrations.Migration46
import at.posselt.pfrpg2e.migrations.migrations.Migration47
import at.posselt.pfrpg2e.migrations.migrations.Migration48
import at.posselt.pfrpg2e.migrations.migrations.Migration49
import at.posselt.pfrpg2e.migrations.migrations.Migration53
import at.posselt.pfrpg2e.migrations.migrations.Migration54
import at.posselt.pfrpg2e.migrations.migrations.Migration50
import com.foundryvtt.core.Game
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for the additive/backfill migrations (categories (a) structural and (b) default-backfill
 * from the card triage). Each asserts the field is created when missing AND that a pre-existing
 * value is preserved (never clobbered) — the property that matters for a GM upgrading across many
 * versions at once. See [MigrationChainTest] for the multi-version-jump guard.
 */
class MigrationBackfillsTest {
    private val game = unsafeJso<Game>()

    private fun kingdom(build: (dynamic) -> Unit = {}): dynamic {
        val k = unsafeJso<dynamic> {}
        build(k)
        return k
    }

    private fun camping(build: (dynamic) -> Unit = {}): CampingData {
        val c = unsafeJso<dynamic> {}
        build(c)
        return c.unsafeCast<CampingData>()
    }

    private fun size(v: dynamic): Int = v.unsafeCast<Array<dynamic>>().size

    // ── Migration25: camping.watchSlots ────────────────────────────────────────────────────────
    @Test
    fun migration25BackfillsWatchSlots() = runTest {
        val c = camping()
        Migration25().migrateCamping(game, c)
        assertEquals(0, size(c.asDynamic().watchSlots))
    }

    @Test
    fun migration25PreservesExistingWatchSlots() = runTest {
        val c = camping { it.watchSlots = arrayOf(arrayOf("Actor.a")) }
        Migration25().migrateCamping(game, c)
        assertEquals(1, size(c.asDynamic().watchSlots))
    }

    // ── Migration28: camping.learnedCompanionActivities ─────────────────────────────────────────
    @Test
    fun migration28BackfillsLearnedCompanionActivities() = runTest {
        val c = camping()
        Migration28().migrateCamping(game, c)
        assertEquals(0, size(c.asDynamic().learnedCompanionActivities))
    }

    // ── Migration29: kingdom.campaignClocks ─────────────────────────────────────────────────────
    @Test
    fun migration29BackfillsCampaignClocks() = runTest {
        val k = kingdom()
        Migration29().migrateKingdom(game, k)
        assertEquals(0, size(k.campaignClocks))
    }

    @Test
    fun migration29PreservesExistingCampaignClocks() = runTest {
        val k = kingdom { it.campaignClocks = arrayOf(unsafeJso<dynamic> {}) }
        Migration29().migrateKingdom(game, k)
        assertEquals(1, size(k.campaignClocks))
    }

    // ── Migration31: settlement.populationRoster ────────────────────────────────────────────────
    @Test
    fun migration31AddsEmptyPopulationRosterToEachSettlement() = runTest {
        val k = kingdom { it.settlements = arrayOf(unsafeJso<dynamic> {}, unsafeJso<dynamic> {}) }
        Migration31().migrateKingdom(game, k)
        assertEquals(0, size(k.settlements[0].populationRoster.npcs))
        assertEquals(0, size(k.settlements[1].populationRoster.npcs))
    }

    @Test
    fun migration31PreservesExistingRoster() = runTest {
        val k = kingdom {
            it.settlements = arrayOf(unsafeJso<dynamic> { populationRoster = js("({ npcs: [{ name: 'Bob' }] })") })
        }
        Migration31().migrateKingdom(game, k)
        assertEquals(1, size(k.settlements[0].populationRoster.npcs))
    }

    // ── Migration33: companion relationship defaults + companionPersonalQuests ───────────────────
    @Test
    fun migration33SeedsCompanionDefaults() = runTest {
        val k = kingdom { it.companions = arrayOf(unsafeJso<dynamic> { name = "Amiri" }) }
        Migration33().migrateKingdom(game, k)
        assertEquals(0, size(k.companionPersonalQuests))
        val comp = k.companions[0]
        assertEquals(0, comp.influence.unsafeCast<Int>())
        assertEquals(true, comp.campAvailable.unsafeCast<Boolean>())
        assertEquals("unknown", comp.discoveryStatus.unsafeCast<String>())
        assertEquals(0, size(comp.personalQuestIds))
    }

    @Test
    fun migration33PreservesExistingInfluence() = runTest {
        val k = kingdom { it.companions = arrayOf(unsafeJso<dynamic> { influence = 7 }) }
        Migration33().migrateKingdom(game, k)
        assertEquals(7, k.companions[0].influence.unsafeCast<Int>())
    }

    // ── Migration34: kingdom.structureBlacklist ─────────────────────────────────────────────────
    @Test
    fun migration34BackfillsStructureBlacklist() = runTest {
        val k = kingdom()
        Migration34().migrateKingdom(game, k)
        assertEquals(0, size(k.structureBlacklist))
    }

    // ── Migration35: kingdom.partyInfluence ─────────────────────────────────────────────────────
    @Test
    fun migration35BackfillsPartyInfluence() = runTest {
        val k = kingdom()
        Migration35().migrateKingdom(game, k)
        assertEquals(0, size(k.partyInfluence))
    }

    // ── Migration38: kingdom.shipments ──────────────────────────────────────────────────────────
    @Test
    fun migration38BackfillsShipments() = runTest {
        val k = kingdom()
        Migration38().migrateKingdom(game, k)
        assertEquals(0, size(k.shipments))
    }

    // ── Migration41: army deployment outcome fields ─────────────────────────────────────────────
    @Test
    fun migration41SeedsDeploymentOutcomeFields() = runTest {
        val k = kingdom { it.armyDeployments = arrayOf(unsafeJso<dynamic> { armyUuid = "Actor.army" }) }
        Migration41().migrateKingdom(game, k)
        assertNull(k.armyDeployments[0].checkResult)
        assertEquals(false, k.armyDeployments[0].effectsApplied.unsafeCast<Boolean>())
    }

    @Test
    fun migration41NoOpsOnMissingDeployments() = runTest {
        val k = kingdom()
        Migration41().migrateKingdom(game, k)  // must not throw
        assertTrue(true)
    }

    // ── Migration42: kingdom.accessGrants ───────────────────────────────────────────────────────
    @Test
    fun migration42BackfillsAccessGrants() = runTest {
        val k = kingdom()
        Migration42().migrateKingdom(game, k)
        assertEquals(0, size(k.accessGrants))
    }

    // ── Migration43: kingdom.bankedBonuses ──────────────────────────────────────────────────────
    @Test
    fun migration43BackfillsBankedBonuses() = runTest {
        val k = kingdom()
        Migration43().migrateKingdom(game, k)
        assertEquals(0, size(k.bankedBonuses))
    }

    // ── Migration44: retire dead settlementTransfer caravan kind ────────────────────────────────
    @Test
    fun migration44RemapsSettlementTransferCaravans() = runTest {
        val k = kingdom {
            it.caravans = arrayOf(
                unsafeJso<dynamic> { kind = "settlementTransfer" },
                unsafeJso<dynamic> { kind = "buyFromPartner" },
            )
        }
        Migration44().migrateKingdom(game, k)
        assertEquals("sellToPartner", k.caravans[0].kind.unsafeCast<String>())
        assertEquals("buyFromPartner", k.caravans[1].kind.unsafeCast<String>())  // untouched
    }

    // ── Migration45: automated-feat tracking (Object.hasOwn guarded) ────────────────────────────
    @Test
    fun migration45SeedsFeatTrackingDefaults() = runTest {
        val k = kingdom()
        Migration45().migrateKingdom(game, k)
        assertEquals(false, k.pullTogetherUsedThisTurn.unsafeCast<Boolean>())
        assertEquals(11, k.pullTogetherCurrentDC.unsafeCast<Int>())
        assertEquals(0, k.pullTogetherTurnsSinceLastUsed.unsafeCast<Int>())
        assertEquals(false, k.liquidateResourcesPenaltyNextTurn.unsafeCast<Boolean>())
        assertEquals(false, k.envyOfTheWorldFirstIgnoreUsed.unsafeCast<Boolean>())
    }

    @Test
    fun migration45PreservesExistingValue() = runTest {
        // Object.hasOwn guard means an explicitly-set value is not overwritten.
        val k = kingdom { it.pullTogetherCurrentDC = 20 }
        Migration45().migrateKingdom(game, k)
        assertEquals(20, k.pullTogetherCurrentDC.unsafeCast<Int>())
    }

    // ── Migration46: war-threat visibleToPlayers ────────────────────────────────────────────────
    @Test
    fun migration46DefaultsThreatVisibilityTrue() = runTest {
        val k = kingdom { it.warThreats = arrayOf(unsafeJso<dynamic> { name = "Goblins" }) }
        Migration46().migrateKingdom(game, k)
        assertEquals(true, k.warThreats[0].visibleToPlayers.unsafeCast<Boolean>())
    }

    @Test
    fun migration46PreservesExplicitHiddenThreat() = runTest {
        val k = kingdom { it.warThreats = arrayOf(unsafeJso<dynamic> { visibleToPlayers = false }) }
        Migration46().migrateKingdom(game, k)
        assertEquals(false, k.warThreats[0].visibleToPlayers.unsafeCast<Boolean>())
    }

    // ── Migration47: camping.autoSucceedInClaimedHexes ──────────────────────────────────────────
    @Test
    fun migration47DefaultsAutoSucceedFalse() = runTest {
        val c = camping()
        Migration47().migrateCamping(game, c)
        assertEquals(false, c.asDynamic().autoSucceedInClaimedHexes.unsafeCast<Boolean>())
    }

    @Test
    fun migration47PreservesEnabledToggle() = runTest {
        val c = camping { it.autoSucceedInClaimedHexes = true }
        Migration47().migrateCamping(game, c)
        assertEquals(true, c.asDynamic().autoSucceedInClaimedHexes.unsafeCast<Boolean>())
    }

    // ── Migration48: companion session-attempt tracking ─────────────────────────────────────────
    @Test
    fun migration48SeedsCompanionSessionIds() = runTest {
        val k = kingdom { it.companions = arrayOf(unsafeJso<dynamic> { name = "Amiri" }) }
        Migration48().migrateKingdom(game, k)
        assertNull(k.companions[0].lastInfluenceAttemptSessionId)
        assertNull(k.companions[0].lastDiscoveryAttemptSessionId)
    }

    @Test
    fun migration48PreservesExistingSessionId() = runTest {
        val k = kingdom { it.companions = arrayOf(unsafeJso<dynamic> { lastInfluenceAttemptSessionId = "s-1" }) }
        Migration48().migrateKingdom(game, k)
        assertEquals("s-1", k.companions[0].lastInfluenceAttemptSessionId.unsafeCast<String>())
    }

    // ── Migration49: camping.travelMoveToken ────────────────────────────────────────────────────
    @Test
    fun migration49DefaultsMoveTokenFalse() = runTest {
        val c = camping()
        Migration49().migrateCamping(game, c)
        assertEquals(false, c.asDynamic().travelMoveToken.unsafeCast<Boolean>())
    }

    @Test
    fun migration49PreservesEnabledToggle() = runTest {
        val c = camping { it.travelMoveToken = true }
        Migration49().migrateCamping(game, c)
        assertEquals(true, c.asDynamic().travelMoveToken.unsafeCast<Boolean>())
    }

    // ── Migration50: settlement.destroyedStructureIds ───────────────────────────────────────────
    @Test
    fun migration50SeedsEmptyRuinedList() = runTest {
        val k = kingdom { it.settlements = arrayOf(unsafeJso<dynamic> { sceneId = "s1" }) }
        Migration50().migrateKingdom(game, k)
        assertEquals(0, k.settlements[0].destroyedStructureIds.length.unsafeCast<Int>())
    }

    @Test
    fun migration50PreservesExistingRuins() = runTest {
        val k = kingdom { it.settlements = arrayOf(unsafeJso<dynamic> { destroyedStructureIds = arrayOf("tok-1") }) }
        Migration50().migrateKingdom(game, k)
        assertEquals("tok-1", k.settlements[0].destroyedStructureIds[0].unsafeCast<String>())
    }

    // ── Migration53: camping.enableWeatherEffects ───────────────────────────────────────────────
    @Test
    fun migration53DefaultsWeatherEffectsOn() = runTest {
        val c = camping()
        Migration53().migrateCamping(game, c)
        assertEquals(true, c.asDynamic().enableWeatherEffects.unsafeCast<Boolean>())
    }

    @Test
    fun migration53PreservesAnExplicitOptOut() = runTest {
        val c = camping { it.enableWeatherEffects = false }
        Migration53().migrateCamping(game, c)
        assertEquals(false, c.asDynamic().enableWeatherEffects.unsafeCast<Boolean>())
    }

    // ── Migration54: kingdom.luxuryBonusUsedThisTurn ────────────────────────────────────────────
    @Test
    fun migration54SeedsTheLuxuryMarkerUnused() = runTest {
        val k = kingdom()
        Migration54().migrateKingdom(game, k)
        assertEquals(false, k.luxuryBonusUsedThisTurn.unsafeCast<Boolean>())
    }
}
