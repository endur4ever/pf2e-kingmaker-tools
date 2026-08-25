package at.posselt.pfrpg2e.kingdom.pings

import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.createRawCompanionExpedition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerPingsAdapterTest {
    private fun expedition(
        id: String,
        status: String,
        daysRemaining: Int,
        visibleToPlayers: Boolean,
    ): RawCompanionExpedition = createRawCompanionExpedition(
        id = id,
        activityId = "scout",
        title = "Expedition $id",
        companionIds = arrayOf("Amiri"),
        totalDays = 40,
        dc = 15,
        tier = "standard",
        visibleToPlayers = visibleToPlayers,
    ).also { it.status = status; it.daysRemaining = daysRemaining }

    @Test
    fun gmOnlyExpeditionsNeverBecomePlayerLines() {
        // the playerSafe filter is the expeditions surface's own visibleToPlayers flag (plan SS5.3)
        val lines = expeditionWhisperLines(
            arrayOf(
                expedition("secret", "inProgress", daysRemaining = 3, visibleToPlayers = false),
                expedition("public", "inProgress", daysRemaining = 3, visibleToPlayers = true),
            )
        )
        assertEquals(1, lines.size)
        assertEquals("Expedition public", lines[0].labelArgs["title"])
    }

    @Test
    fun onlyExpeditionsInsideTheTurnHorizonAreReturning() {
        val lines = expeditionWhisperLines(
            arrayOf(
                expedition("soon", "inProgress", daysRemaining = 30, visibleToPlayers = true),
                expedition("far", "inProgress", daysRemaining = 31, visibleToPlayers = true),
            )
        )
        assertEquals(listOf("Expedition soon"), lines.map { it.labelArgs["title"] })
        assertEquals("30", lines[0].labelArgs["days"])
    }

    @Test
    fun awaitingResolutionGetsItsOwnLineRegardlessOfDays()  {
        val lines = expeditionWhisperLines(
            arrayOf(expedition("back", "awaitingResolution", daysRemaining = 0, visibleToPlayers = true))
        )
        assertEquals(1, lines.size)
        assertEquals(PINGS_KEY_EXPEDITION_AWAITING, lines[0].labelKey)
    }

    @Test
    fun resolvedAndNullInputsProduceNothing() {
        assertTrue(expeditionWhisperLines(null).isEmpty())
        assertTrue(
            expeditionWhisperLines(
                arrayOf(expedition("done", "resolved", daysRemaining = 0, visibleToPlayers = true))
            ).isEmpty()
        )
    }

    @Test
    fun allLinesJumpToTheExpeditionsTab() {
        val lines = expeditionWhisperLines(
            arrayOf(
                expedition("a", "inProgress", daysRemaining = 2, visibleToPlayers = true),
                expedition("b", "awaitingResolution", daysRemaining = 0, visibleToPlayers = true),
            )
        )
        assertTrue(lines.all { it.jumpKind == "sheet-tab" && it.jumpValue == "expeditions" })
    }
}
