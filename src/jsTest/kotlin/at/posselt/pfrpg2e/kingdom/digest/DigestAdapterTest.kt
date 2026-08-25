package at.posselt.pfrpg2e.kingdom.digest

import at.posselt.pfrpg2e.kingdom.CaravanEvent
import at.posselt.pfrpg2e.kingdom.CaravanEventKind
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.data.createRawExpeditionChronicleEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DigestAdapterTest {
    private fun caravanEvent(
        kind: CaravanEventKind,
        cargoLost: Int = 0,
        deliveredAmount: Int = 0,
        bonusResourceDice: Int = 0,
        cargoAmount: Int = 10,
        destLabel: String? = "Restov",
        partnerName: String? = "Restov Traders",
    ) = CaravanEvent(
        kind = kind,
        summary = "Caravan to somewhere",
        partnerName = partnerName,
        bonusResourceDice = bonusResourceDice,
        deliveredCommodity = "lumber",
        deliveredAmount = deliveredAmount,
        cargoLost = cargoLost,
        cargoAmount = cargoAmount,
        cargoCommodity = "lumber",
        originLabel = "Capital",
        destLabel = destLabel,
    )

    private fun chronicleEntry(title: String, outcomeDegree: String, turn: Int) =
        createRawExpeditionChronicleEntry(
            title = title,
            companionNames = "Amiri, Linzi",
            activityId = "scout",
            outcomeDegree = outcomeDegree,
            lootRp = 2,
            factionStandingDelta = 0,
            targetFactionName = null,
            turn = turn,
            appliedAt = "2024-01-01T00:00:00Z",
        )

    private fun threat(id: String, escalationLevel: Int, visibleToPlayers: Boolean?) = RawWarThreat(
        id = id, name = "Threat $id", description = "raiders", enemyFaction = null,
        escalationLevel = escalationLevel, maxEscalation = 4, eta = 2,
        targetSettlementSceneId = null, targetHexLocation = null,
        linkedQuestId = null, linkedEventId = null, pauseOnExpiry = false,
        status = "active", triggeredTurn = null,
    ).also { it.visibleToPlayers = visibleToPlayers }

    @Test
    fun raidMagnitudeUsesThePreTickCargoDenominator() {
        val events = caravanDigestEvents(
            listOf(caravanEvent(CaravanEventKind.RAIDED, cargoLost = 2, cargoAmount = 10)),
            feedKind = "caravan",
            turn = 5,
        )
        assertEquals(0.2, events[0].magnitudeNorm, 1e-9, "losing 2 of 10, not 2 of 2")
        assertEquals("kingdom.meanwhile.caravan.raided", events[0].labelKey)
        assertEquals("2", events[0].labelArgs["lost"])
        assertEquals("caravan-5-0", events[0].id)
    }

    @Test
    fun deliveryBranchesOnBonusDiceVersusCommodityAmount() {
        val events = caravanDigestEvents(
            listOf(
                caravanEvent(CaravanEventKind.DELIVERED, bonusResourceDice = 2),
                caravanEvent(CaravanEventKind.DELIVERED, deliveredAmount = 4, destLabel = "Varnhold"),
                caravanEvent(CaravanEventKind.LOST, destLabel = "Pitax"),
            ),
            feedKind = "shipment",
            turn = 3,
        )
        assertEquals("kingdom.meanwhile.shipment.deliveredRd", events[0].labelKey)
        assertEquals("2", events[0].labelArgs["rd"])
        assertEquals("kingdom.meanwhile.shipment.deliveredAmount", events[1].labelKey)
        assertEquals("4", events[1].labelArgs["amount"])
        assertEquals("lumber", events[1].labelArgs["commodity"])
        assertEquals("kingdom.meanwhile.shipment.lost", events[2].labelKey)
        // the delivered side of the magnitude wiring: 4 of 10 delivered scores 0.4
        assertEquals(0.4, events[1].magnitudeNorm, 1e-9)
        assertEquals(RELEVANCE_CARAVAN_TIER, events[1].relevance, 1e-9)
    }

    @Test
    fun destinationFallsBackFromLabelToPartnerToSummary() {
        val events = caravanDigestEvents(
            listOf(
                caravanEvent(CaravanEventKind.LOST, destLabel = null, partnerName = "Restov Traders"),
                caravanEvent(CaravanEventKind.LOST, destLabel = null, partnerName = null),
            ),
            feedKind = "caravan",
            turn = 1,
        )
        assertEquals("Restov Traders", events[0].sourceName)
        assertEquals("Caravan to somewhere", events[1].sourceName)
    }

    @Test
    fun chronicleEventsAreScopedToTheDigestedTurn() {
        val events = expeditionDigestEvents(
            arrayOf(
                chronicleEntry("Old Trip", "success", turn = 4),
                chronicleEntry("This Trip", "criticalSuccess", turn = 5),
                // a future-turn entry (undone turn remnant) must be excluded too: == not >=
                chronicleEntry("Future Trip", "success", turn = 6),
            ),
            turn = 5,
        )
        assertEquals(listOf("This Trip"), events.map { it.sourceName })
        assertEquals("kingdom.meanwhile.expedition.criticalSuccess", events[0].labelKey)
    }

    @Test
    fun bothCriticalDegreesOutscoreThePlainOnes() {
        val magnitudes = listOf("criticalSuccess", "criticalFailure", "success", "failure", "weird")
            .map { degree ->
                expeditionDigestEvents(arrayOf(chronicleEntry("T", degree, 1)), turn = 1)[0]
            }
        assertEquals(1.0, magnitudes[0].magnitudeNorm)
        assertEquals(1.0, magnitudes[1].magnitudeNorm)
        assertTrue(magnitudes[2].magnitudeNorm > magnitudes[3].magnitudeNorm)
        assertTrue(magnitudes[1].magnitudeNorm > magnitudes[2].magnitudeNorm)
        // an unknown degree renders through the safe template, never a raw-key label,
        // and scores midfield -- neither buried nor headline
        assertEquals("kingdom.meanwhile.expedition.success", magnitudes[4].labelKey)
        assertEquals(0.5, magnitudes[4].magnitudeNorm, 1e-9)
        assertEquals(RELEVANCE_EXPEDITION_TIER, magnitudes[0].relevance, 1e-9)
    }

    @Test
    fun onlyVisiblyEscalatedThreatsBecomeBeats() {
        val pre = listOf(
            threat("a", escalationLevel = 1, visibleToPlayers = true),
            threat("b", escalationLevel = 1, visibleToPlayers = true),
            threat("c", escalationLevel = 1, visibleToPlayers = false),
            threat("legacy-null", escalationLevel = 1, visibleToPlayers = null),
        )
        val post = listOf(
            threat("a", escalationLevel = 2, visibleToPlayers = true),   // escalated + visible => beat
            threat("b", escalationLevel = 1, visibleToPlayers = true),   // unchanged => no beat
            threat("c", escalationLevel = 3, visibleToPlayers = false),  // escalated but hidden
            // null = VISIBLE: the field's own contract (Migration46 backfills null to true) and
            // how the Army Pressure board renders it -- the digest must not under-report
            threat("legacy-null", escalationLevel = 2, visibleToPlayers = null),
            threat("new", escalationLevel = 2, visibleToPlayers = true), // no pre-tick row => no diff
        )
        val events = threatEscalationEvents(pre, post, turn = 7)
        assertEquals(listOf("threat-a-esc2", "threat-legacy-null-esc2"), events.map { it.id })
        assertEquals(0.5, events[0].magnitudeNorm, 1e-9, "level 2 of max 4")
        assertEquals("2", events[0].labelArgs["level"])
        assertEquals("4", events[0].labelArgs["max"])
        assertEquals(RELEVANCE_THREAT_TIER, events[0].relevance, 1e-9)
    }

    @Test
    fun zeroMaxEscalationCannotBlowTheMagnitudeContract() {
        val pre = listOf(threat("z", escalationLevel = 0, visibleToPlayers = true).also { it.maxEscalation = 0 })
        val post = listOf(threat("z", escalationLevel = 1, visibleToPlayers = true).also { it.maxEscalation = 0 })
        val events = threatEscalationEvents(pre, post, turn = 1)
        assertEquals(1, events.size)
        assertTrue(events[0].magnitudeNorm in 0.0..1.0, "guarded division, never Infinity")
    }

    @Test
    fun feedKindThreadingIsPinnedAtTheAssemblySeam() {
        // the call-site swap mutant: caravans narrated with shipment prose and vice versa
        val events = buildEndTurnDigestEvents(
            caravanEvents = listOf(caravanEvent(CaravanEventKind.LOST, destLabel = "CaravanDest")),
            shipmentEvents = listOf(caravanEvent(CaravanEventKind.LOST, destLabel = "ShipmentDest")),
            expeditionChronicle = arrayOf(chronicleEntry("Trip", "success", turn = 9)),
            preTickThreats = listOf(threat("t", escalationLevel = 1, visibleToPlayers = true)),
            postTickThreats = listOf(threat("t", escalationLevel = 2, visibleToPlayers = true)),
            turn = 9,
        )
        val byKind = events.associateBy { it.kind }
        assertEquals(4, events.size)
        assertEquals("CaravanDest", byKind["caravan"]?.sourceName)
        assertEquals("kingdom.meanwhile.caravan.lost", byKind["caravan"]?.labelKey)
        assertEquals("ShipmentDest", byKind["shipment"]?.sourceName)
        assertEquals("kingdom.meanwhile.shipment.lost", byKind["shipment"]?.labelKey)
        assertEquals("Trip", byKind["expedition"]?.sourceName)
        assertEquals("threat-t-esc2", byKind["warThreat"]?.id)
    }

    @Test
    fun dedupBaselineOnlyTrustsTheImmediatelyPreviousTurn() {
        val record = js("{}").unsafeCast<RawDigestRecord>()
        record.turn = 4
        record.beatIds = arrayOf("a", "b")
        assertEquals(setOf("a", "b"), digestDedupBaseline(record, turn = 5))
        assertEquals(emptySet(), digestDedupBaseline(record, turn = 4), "same turn is not a baseline")
        assertEquals(emptySet(), digestDedupBaseline(record, turn = 6), "a missed turn stales the baseline")
        assertEquals(emptySet(), digestDedupBaseline(null, turn = 5))
        val malformed = js("{}").unsafeCast<RawDigestRecord>()
        malformed.turn = 4
        // beatIds left undefined: a malformed flag reads as no baseline, never a throw
        assertEquals(emptySet(), digestDedupBaseline(malformed, turn = 5))
    }

    @Test
    fun commodityFallsBackFromCargoToDeliveredToGeneric() {
        fun event(cargo: String?, delivered: String?) = CaravanEvent(
            kind = CaravanEventKind.RAIDED,
            summary = "s",
            cargoLost = 1,
            cargoAmount = 5,
            cargoCommodity = cargo,
            deliveredCommodity = delivered,
            destLabel = "X",
        )
        val args = { c: String?, d: String? ->
            caravanDigestEvents(listOf(event(c, d)), "caravan", 1)[0].labelArgs["commodity"]!!
        }
        assertEquals("ore", args("ore", "lumber"), "cargoCommodity wins")
        assertEquals("lumber", args(null, "lumber"))
        assertTrue(args(null, null).contains("genericCargo"), "both null falls to the localized generic")
    }

    @Test
    fun caravansOutrankExpeditionsOutrankThreatsAtEqualMagnitude() {
        // relevance carries 1.5x weight; the tiers ARE the cross-feed ranking
        val caravan = caravanDigestEvents(
            listOf(caravanEvent(CaravanEventKind.RAIDED, cargoLost = 10, cargoAmount = 10)),
            feedKind = "caravan", turn = 1,
        )
        val expedition = expeditionDigestEvents(arrayOf(chronicleEntry("T", "criticalSuccess", 1)), turn = 1)
        val threats = threatEscalationEvents(
            listOf(threat("t", escalationLevel = 3, visibleToPlayers = true)),
            listOf(threat("t", escalationLevel = 4, visibleToPlayers = true)),
            turn = 1,
        )
        val beats = selectDigestBeats(threats + expedition + caravan)
        assertEquals(listOf("caravan", "expedition", "warThreat"), beats.map { it.kind })
    }

    @Test
    fun configDefaultsAreOnAndFourWithHardClamps() {
        assertTrue(digestEnabled(null), "null = shipped-on default")
        assertTrue(digestEnabled(true))
        assertEquals(false, digestEnabled(false))
        assertEquals(MAX_DIGEST_BEATS, digestBeatCap(null))
        assertEquals(1, digestBeatCap(0), "0 clamps up -- disabling is the checkbox, not the cap")
        assertEquals(1, digestBeatCap(-3))
        assertEquals(6, digestBeatCap(99), "plan's 1..6 ceiling")
        assertEquals(3, digestBeatCap(3))
    }

    @Test
    fun selectionCapsAndDedupsPerRoute() {
        // two events on the same route: per-source dedup keeps the higher-scoring one
        val sameRoute = caravanDigestEvents(
            listOf(
                caravanEvent(CaravanEventKind.RAIDED, cargoLost = 1, cargoAmount = 10),
                caravanEvent(CaravanEventKind.RAIDED, cargoLost = 8, cargoAmount = 10),
            ),
            feedKind = "caravan",
            turn = 2,
        )
        val beats = selectDigestBeats(sameRoute)
        assertEquals(1, beats.size)
        assertEquals("caravan-2-1", beats[0].id, "the bigger raid wins the route's single beat")
    }
}
