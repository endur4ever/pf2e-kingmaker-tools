package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawInfluenceEncounter
import at.posselt.pfrpg2e.kingdom.data.RawInfluenceTrait
import at.posselt.pfrpg2e.kingdom.data.RawResearchProject
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemCheck
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemCheckEntry
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemStore
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemThreshold
import at.posselt.pfrpg2e.kingdom.dialogs.checksToLines
import at.posselt.pfrpg2e.kingdom.dialogs.mergeRevealed
import at.posselt.pfrpg2e.kingdom.dialogs.mergeThresholdState
import at.posselt.pfrpg2e.kingdom.dialogs.parseCheckLines
import at.posselt.pfrpg2e.kingdom.dialogs.parseThresholdLines
import at.posselt.pfrpg2e.kingdom.dialogs.parseTraitLines
import at.posselt.pfrpg2e.kingdom.dialogs.clampResearchApplication
import at.posselt.pfrpg2e.kingdom.dialogs.matchedTraitsFor
import at.posselt.pfrpg2e.data.kingdom.subsystems.PointApplication
import at.posselt.pfrpg2e.kingdom.sheet.contexts.buildSubsystemTrackersContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun encounter(
    id: String = "enc-1",
    visible: Boolean? = null,
    discoveries: Array<RawSubsystemCheck>? = null,
    thresholds: Array<RawSubsystemThreshold>? = null,
    resistances: Array<RawInfluenceTrait>? = null,
    checkLog: Array<RawSubsystemCheckEntry>? = null,
    points: Int = 0,
) = RawInfluenceEncounter(
    id = id,
    name = "Restov Banquet",
    npcName = "Lady Aldori",
    description = "",
    influencePoints = points,
    status = "active",
    discoveries = discoveries,
    influenceSkills = null,
    thresholds = thresholds,
    resistances = resistances,
    weaknesses = null,
    participants = null,
    checkLog = checkLog,
    visibleToPlayers = visible,
)

private fun check(skill: String, dc: Int, revealed: Boolean? = null, note: String? = null) =
    RawSubsystemCheck(skill = skill, dc = dc, revealed = revealed, note = note)

private fun threshold(points: Int, effect: String, revealed: Boolean? = null) =
    RawSubsystemThreshold(points = points, effect = effect, offerConsumed = false, revealedToPlayers = revealed)

class SubsystemContextTest {
    @Test
    fun playerSeesOnlyVisibleEncounters() {
        val store = RawSubsystemStore(
            influenceEncounters = arrayOf(
                encounter(id = "shown", visible = true),
                encounter(id = "hidden", visible = false),
                encounter(id = "unset", visible = null),
            ),
            researchProjects = null,
        )
        val player = buildSubsystemTrackersContext("p", store, isGM = false, partyOptions = emptyList())
        assertEquals(listOf("shown"), player.encounters.map { it.id })
        val gm = buildSubsystemTrackersContext("p", store, isGM = true, partyOptions = emptyList())
        assertEquals(3, gm.encounters.size)
    }

    @Test
    fun playerSeesOnlyRevealedChecksWithoutGmNotes() {
        val store = RawSubsystemStore(
            influenceEncounters = arrayOf(
                encounter(
                    visible = true,
                    discoveries = arrayOf(
                        check("society", 18, revealed = false, note = "secret"),
                        check("diplomacy", 20, revealed = true, note = "gm eyes only"),
                    ),
                ),
            ),
            researchProjects = null,
        )
        val player = buildSubsystemTrackersContext("p", store, isGM = false, partyOptions = emptyList())
        val rows = player.encounters.single().discoveries
        assertEquals(1, rows.size)
        assertEquals("diplomacy", rows.single().skill)
        // index must survive the filter: it is the reveal-toggle's address into the raw array
        assertEquals(1, rows.single().index)
        assertNull(rows.single().note)
        val gm = buildSubsystemTrackersContext("p", store, isGM = true, partyOptions = emptyList())
        assertEquals(2, gm.encounters.single().discoveries.size)
        assertEquals("gm eyes only", gm.encounters.single().discoveries[1].note)
    }

    @Test
    fun thresholdEffectHiddenUntilRevealed() {
        val store = RawSubsystemStore(
            influenceEncounters = arrayOf(
                encounter(
                    visible = true,
                    points = 4,
                    thresholds = arrayOf(
                        threshold(4, "names the assassin", revealed = true),
                        threshold(6, "betrays the plot", revealed = false),
                    ),
                ),
            ),
            researchProjects = null,
        )
        val player = buildSubsystemTrackersContext("p", store, isGM = false, partyOptions = emptyList())
        val playerRows = player.encounters.single().thresholds
        assertEquals(1, playerRows.size)
        assertEquals("names the assassin", playerRows.single().effect)
        assertTrue(playerRows.single().reached)
        val gm = buildSubsystemTrackersContext("p", store, isGM = true, partyOptions = emptyList())
        val gmRows = gm.encounters.single().thresholds
        assertEquals(2, gmRows.size)
        assertEquals("betrays the plot", gmRows[1].effect)
        assertEquals(false, gmRows[1].reached)
    }

    @Test
    fun resistanceSummaryIsGmOnly() {
        val store = RawSubsystemStore(
            influenceEncounters = arrayOf(
                encounter(visible = true, resistances = arrayOf(RawInfluenceTrait(label = "flattery", delta = -1, note = null))),
            ),
            researchProjects = null,
        )
        val player = buildSubsystemTrackersContext("p", store, isGM = false, partyOptions = emptyList())
        assertNull(player.encounters.single().resistanceSummary)
        val gm = buildSubsystemTrackersContext("p", store, isGM = true, partyOptions = emptyList())
        assertEquals("flattery (-1)", gm.encounters.single().resistanceSummary)
    }

    @Test
    fun logKeepsLastFiveNewestFirstAndLocalizesOutcome() {
        val log = (1..6).map { n ->
            RawSubsystemCheckEntry(
                timestamp = n.toDouble(), participantUuid = null, skill = "skill$n",
                outcome = if (n == 6) "criticalSuccess" else "failure", pointsDelta = if (n == 6) 2 else -1, note = null,
            )
        }.toTypedArray()
        val store = RawSubsystemStore(
            influenceEncounters = arrayOf(encounter(visible = true, checkLog = log)),
            researchProjects = null,
        )
        val rows = buildSubsystemTrackersContext("p", store, isGM = true, partyOptions = emptyList())
            .encounters.single().recentLog
        assertEquals(5, rows.size)
        assertEquals("skill6", rows.first().skill)
        // i18n is not initialized under test, so t() falls back to the literal key
        assertEquals("subsystems.outcome.critSuccess", rows.first().outcome)
        assertEquals("skill2", rows.last().skill)
    }

    @Test
    fun researchProgressPctClampsAndHonorsVisibility() {
        val store = RawSubsystemStore(
            influenceEncounters = null,
            researchProjects = arrayOf(
                RawResearchProject(
                    id = "r1", name = "Vordakai's Library", description = "", researchPoints = 30,
                    maxResearchPoints = 20, status = "active", visibleToPlayers = false,
                ),
            ),
        )
        assertEquals(0, buildSubsystemTrackersContext("p", store, isGM = false, partyOptions = emptyList()).projects.size)
        val gm = buildSubsystemTrackersContext("p", store, isGM = true, partyOptions = emptyList())
        assertEquals(100, gm.projects.single().progressPct)
    }

    @Test
    fun parsersAcceptGoodLinesAndRejectBadOnes() {
        val checks = parseCheckLines("Diplomacy | 20\n\n  intimidation|18 | scares her  ")
        assertEquals(2, checks?.size)
        assertEquals("Diplomacy", checks?.get(0)?.skill)
        assertEquals(18, checks?.get(1)?.dc)
        assertEquals("scares her", checks?.get(1)?.note)
        assertNull(parseCheckLines("Diplomacy | high"))
        assertNull(parseCheckLines("| 20"))

        val thresholds = parseThresholdLines("4 | reveals the assassin")
        assertEquals(4, thresholds?.single()?.points)
        assertNull(parseThresholdLines("4 |"))
        assertNull(parseThresholdLines("four | effect"))

        val traits = parseTraitLines("flattery | -1")
        assertEquals(-1, traits?.single()?.delta)
        assertNull(parseTraitLines("flattery | minus"))
    }

    @Test
    fun roundTripAndMergePreserveRevealState() {
        val existing = arrayOf(check("diplomacy", 20, revealed = true), check("society", 18, revealed = true))
        val reparsed = parseCheckLines(checksToLines(existing))!!
        val merged = mergeRevealed(reparsed, existing)
        assertEquals(true, merged[0].revealed)
        assertEquals(true, merged[1].revealed)
        // an edited row (dc changed) is a NEW secret: reveal state resets
        val edited = parseCheckLines("diplomacy | 25\nsociety | 18")!!
        val remerged = mergeRevealed(edited, existing)
        assertEquals(false, remerged[0].revealed)
        assertEquals(true, remerged[1].revealed)

        val priorThresholds = arrayOf(
            RawSubsystemThreshold(points = 4, effect = "old text", offerConsumed = true, revealedToPlayers = true),
        )
        val mergedThresholds = mergeThresholdState(parseThresholdLines("4 | new text")!!, priorThresholds)
        assertEquals(true, mergedThresholds[0].offerConsumed)
        assertEquals("new text", mergedThresholds[0].effect)
        // a reworded effect is a new secret: the old reveal must NOT follow the slot
        assertEquals(false, mergedThresholds[0].revealedToPlayers)
        val unchanged = mergeThresholdState(parseThresholdLines("4 | old text")!!, priorThresholds)
        assertEquals(true, unchanged[0].revealedToPlayers)
    }

    @Test
    fun pipesInsideNotesAndEffectsSurviveParsing() {
        val checks = parseCheckLines("diplomacy | 20 | mention X | never Y")!!
        assertEquals("mention X | never Y", checks.single().note)
        val thresholds = parseThresholdLines("4 | she reveals A | then B")!!
        assertEquals("she reveals A | then B", thresholds.single().effect)
    }

    @Test
    fun traitMatchingIsNoteSubstringCaseInsensitive() {
        val resistances = arrayOf(RawInfluenceTrait(label = "Flattery", delta = -1, note = null))
        val weaknesses = arrayOf(RawInfluenceTrait(label = "greed", delta = 2, note = null))
        val matched = matchedTraitsFor("appeal to GREED, no flattery", resistances, weaknesses)
        assertEquals(listOf("Flattery", "greed"), matched.map { it.label })
        assertEquals(listOf(-1, 2), matched.map { it.delta })
        assertEquals(0, matchedTraitsFor(null, resistances, weaknesses).size)
        assertEquals(0, matchedTraitsFor("nothing relevant", resistances, weaknesses).size)
    }

    @Test
    fun researchApplicationClampsAtMax() {
        val clamped = clampResearchApplication(19, PointApplication(newTotal = 21, appliedDelta = 2), 20)
        assertEquals(20, clamped.newTotal)
        assertEquals(1, clamped.appliedDelta)
        val unclamped = clampResearchApplication(10, PointApplication(newTotal = 12, appliedDelta = 2), 20)
        assertEquals(12, unclamped.newTotal)
        val noMax = clampResearchApplication(10, PointApplication(newTotal = 12, appliedDelta = 2), null)
        assertEquals(12, noMax.newTotal)
    }
}
