package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.manifestXpTotal
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeKind
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeProject
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeStatus
import at.posselt.pfrpg2e.kingdom.downtime.appendDowntimeProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeadFunsWiringTest {

    @Test
    fun manifestXpTotalComputesExpectedXp() {
        val partyLevel = 3
        // Level 3 (diff 0) = 40 XP, Level 4 (diff +1) = 60 XP
        val creatures = listOf(3 to 2, 4 to 1)
        val total = manifestXpTotal(partyLevel, creatures)
        assertEquals(40 * 2 + 60 * 1, total)

        assertEquals(0, manifestXpTotal(partyLevel, emptyList()))
    }

    @Test
    fun chronicleEntryCountSumsAcrossGroups() {
        val emptyGroups = emptyList<ChronicleTurnGroup>()
        assertEquals(0, chronicleEntryCount(emptyGroups))

        val groups = listOf(
            ChronicleTurnGroup(
                turn = 2,
                entries = listOf(
                    ChronicleEntry(turn = 2, companionName = "Amiri", summary = "Explored"),
                    ChronicleEntry(turn = 2, companionName = "Valerie", summary = "Guarded"),
                ),
            ),
            ChronicleTurnGroup(
                turn = 1,
                entries = listOf(
                    ChronicleEntry(turn = 1, companionName = "Linzi", summary = "Sang"),
                ),
            ),
        )
        assertEquals(3, chronicleEntryCount(groups))
    }

    @Test
    fun canLiquidateResourcesGatingConditions() {
        // Can liquidate when expense pushes RP to 0 or below and not already used
        assertTrue(canLiquidateResources(currentRp = 5, expense = 10, alreadyUsedThisTurn = false))
        assertTrue(canLiquidateResources(currentRp = 5, expense = 5, alreadyUsedThisTurn = false))

        // Cannot liquidate if RP remains above 0
        assertFalse(canLiquidateResources(currentRp = 10, expense = 5, alreadyUsedThisTurn = false))

        // Cannot liquidate if already used this turn
        assertFalse(canLiquidateResources(currentRp = 5, expense = 10, alreadyUsedThisTurn = true))

        // Cannot liquidate if expense is 0
        assertFalse(canLiquidateResources(currentRp = 0, expense = 0, alreadyUsedThisTurn = false))
    }

    @Test
    fun buildAttentionRowsConstructsExpectedRows() {
        val rows = buildAttentionRows(
            questsFailingThisTurn = 2,
            warThreatsAtMax = 1,
            expeditionsAwaiting = 0,
            companionsInjured = 3,
        )

        assertEquals(4, rows.size)

        val questsRow = rows.first { it.id == "questsFailing" }
        assertEquals(2, questsRow.count)
        assertTrue(questsRow.highlight)

        val warThreatsRow = rows.first { it.id == "warThreatsMax" }
        assertEquals(1, warThreatsRow.count)
        assertTrue(warThreatsRow.highlight)

        val expeditionsRow = rows.first { it.id == "expeditionsAwaiting" }
        assertEquals(0, expeditionsRow.count)
        assertFalse(expeditionsRow.highlight)

        val companionsRow = rows.first { it.id == "companionsInjured" }
        assertEquals(3, companionsRow.count)
        assertTrue(companionsRow.highlight)
    }

    @Test
    fun appendDowntimeProjectAppendsAndRespectsCap() {
        fun makeProject(id: String, status: DowntimeStatus = DowntimeStatus.IN_PROGRESS) = DowntimeProject(
            id = id,
            pcActorUuid = "Actor.1",
            kind = DowntimeKind.CRAFT,
            title = "Craft $id",
            daysTotal = 10,
            daysRemaining = if (status == DowntimeStatus.COMPLETED) 0 else 5,
            status = status,
        )

        val p1 = makeProject("1")
        val p2 = makeProject("2")
        val result = appendDowntimeProject(listOf(p1), p2)
        assertEquals(listOf("1", "2"), result.map { it.id })

        // Completed project history pruning with cap = 2
        val c1 = makeProject("c1", DowntimeStatus.COMPLETED)
        val c2 = makeProject("c2", DowntimeStatus.COMPLETED)
        val c3 = makeProject("c3", DowntimeStatus.COMPLETED)
        val pruned = appendDowntimeProject(listOf(c1, c2), c3, cap = 2)
        assertEquals(listOf("c2", "c3"), pruned.map { it.id })
    }
}
