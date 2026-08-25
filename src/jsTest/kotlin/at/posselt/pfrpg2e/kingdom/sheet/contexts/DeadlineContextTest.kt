package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawScheduledPressure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeadlineContextTest {
    private fun pressure(
        id: String,
        startDay: Int,
        recurrence: String = "none",
        payloadKind: String = "postBeat",
        lastFiredDay: Int? = null,
        active: Boolean = true,
        resolveConditionKind: String? = null,
        resolveConditionRef: String? = null,
    ): RawScheduledPressure {
        val obj = js("{}").unsafeCast<RawScheduledPressure>()
        obj.id = id
        obj.name = "P $id"
        obj.startDay = startDay
        obj.recurrence = recurrence
        obj.payloadKind = payloadKind
        obj.escalationCount = 0
        obj.active = active
        obj.lastFiredDay = lastFiredDay
        obj.resolveConditionKind = resolveConditionKind
        obj.resolveConditionRef = resolveConditionRef
        return obj
    }

    private fun kingdom(vararg pressures: RawScheduledPressure): KingdomData {
        val k = js("{}").unsafeCast<KingdomData>()
        k.scheduledPressures = arrayOf(*pressures)
        val quest = js("{}")
        quest.id = "q1"
        quest.status = "completed"
        k.asDynamic().quests = arrayOf(quest)
        return k
    }

    @Test
    fun playersGetNullNeverAnEmptyList() {
        assertNull(buildDeadlinesContext(isGM = false, kingdom = kingdom(pressure("a", 10)), currentDay = 0))
    }

    @Test
    fun gmWithNoSchedulesGetsAnEmptyArrayNotNull() {
        val k = js("{}").unsafeCast<KingdomData>()
        assertEquals(0, buildDeadlinesContext(isGM = true, kingdom = k, currentDay = 0)?.size)
    }

    @Test
    fun countdownStatesCoverTheRowLifecycle() {
        val rows = buildDeadlinesContext(
            isGM = true,
            kingdom = kingdom(
                pressure("future", startDay = 15),
                pressure("fired", startDay = 5, lastFiredDay = 5),
                pressure("off", startDay = 20, active = false),
                pressure("done", startDay = 30, resolveConditionKind = "questCompleted", resolveConditionRef = "q1"),
                pressure("weird", startDay = 3, recurrence = "fortnightly"),
            ),
            currentDay = 10,
        )!!
        val byId = rows.associateBy { it.id }
        assertTrue(byId["future"]!!.countdownLabel.contains("5"), "in 5 days")
        assertTrue(byId["fired"]!!.countdownLabel.contains("ended"))
        assertTrue(byId["off"]!!.inactive)
        assertTrue(byId["off"]!!.countdownLabel.contains("inactive"), "inactive wins the countdown cell")
        assertTrue(byId["done"]!!.resolved)
        assertTrue(byId["done"]!!.countdownLabel.contains("resolved"), "resolved wins over a future firing")
        assertEquals(5, rows.size, "an unknown recurrence still shows its row for the GM to fix")
        assertTrue(byId["weird"]!!.recurrenceLabel.contains("unknown"))
    }

    @Test
    fun resolveLabelOnlyForLinkedSchedules() {
        val rows = buildDeadlinesContext(
            isGM = true,
            kingdom = kingdom(
                pressure("linked", startDay = 15, resolveConditionKind = "threatResolved", resolveConditionRef = "t1"),
                pressure("free", startDay = 15),
            ),
            currentDay = 0,
        )!!
        val byId = rows.associateBy { it.id }
        assertTrue(byId["linked"]!!.resolveLabel != null)
        assertNull(byId["free"]!!.resolveLabel)
    }

    @Test
    fun recurringRowCountsToTheNextStrideFiring() {
        // weekly from day 3, last fired day 10 -> next is 17; from day 12 that is "in 5 days"
        val rows = buildDeadlinesContext(
            isGM = true,
            kingdom = kingdom(pressure("w", startDay = 3, recurrence = "weekly", lastFiredDay = 10)),
            currentDay = 12,
        )!!
        assertTrue(rows[0].countdownLabel.contains("5"))
    }
}
