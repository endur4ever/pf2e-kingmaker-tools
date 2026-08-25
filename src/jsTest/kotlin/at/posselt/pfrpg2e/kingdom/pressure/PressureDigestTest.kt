package at.posselt.pfrpg2e.kingdom.pressure

import at.posselt.pfrpg2e.kingdom.data.RawScheduledPressure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PressureDigestTest {
    private fun raw(
        id: String,
        payloadKind: String = "postBeat",
        beatText: String? = null,
        lastFiredDay: Int? = null,
        lastHandledDay: Int? = null,
    ): RawScheduledPressure {
        val obj = js("{}").unsafeCast<RawScheduledPressure>()
        obj.id = id
        obj.name = "Schedule $id"
        obj.startDay = 0
        obj.recurrence = "daily"
        obj.payloadKind = payloadKind
        obj.payloadBeatText = beatText
        obj.escalationCount = 0
        obj.active = true
        obj.lastFiredDay = lastFiredDay
        obj.lastHandledDay = lastHandledDay
        return obj
    }

    private fun schedule(id: String) = ScheduledPressure(
        id = id, name = "Schedule $id", startDay = 0,
        recurrence = Recurrence.DAILY, payloadKind = PayloadKind.POST_BEAT,
    )

    private fun firing(id: String, day: Int, escalation: Int = 1) =
        PressureFiring(schedule = schedule(id), day = day, escalation = escalation)

    @Test
    fun rowsGroupByDayInAscendingOrder() {
        val ctx = buildPressureDigestContext(
            firings = listOf(firing("b", day = 10), firing("a", day = 8), firing("c", day = 10)),
            rawById = mapOf("a" to raw("a"), "b" to raw("b"), "c" to raw("c")),
            actorUuid = "Actor.x",
            currentDay = 10,
        )
        assertEquals(2, ctx.days.size)
        assertEquals(listOf("a"), ctx.days[0].rows.map { it.scheduleId }, "older day first")
        assertEquals(listOf("b", "c"), ctx.days[1].rows.map { it.scheduleId })
        // day 10 == currentDay renders as "today", day 8 as a days-ago label
        assertTrue(ctx.days[1].dayLabel != ctx.days[0].dayLabel)
    }

    @Test
    fun beatPreviewOnlyForPostBeatAndTruncated() {
        val long = "x".repeat(200)
        val ctx = buildPressureDigestContext(
            firings = listOf(firing("beat", day = 1), firing("clock", day = 1)),
            rawById = mapOf(
                "beat" to raw("beat", payloadKind = "postBeat", beatText = long),
                "clock" to raw("clock", payloadKind = "advanceClock", beatText = long),
            ),
            actorUuid = "A",
            currentDay = 1,
        )
        val byId = ctx.days[0].rows.associateBy { it.scheduleId }
        assertEquals(80, byId["beat"]?.preview?.length, "preview capped")
        assertNull(byId["clock"]?.preview, "non-beat rows carry no preview")
    }

    @Test
    fun escalationLabelOnlyPastTheFirstFiring() {
        val ctx = buildPressureDigestContext(
            firings = listOf(firing("first", day = 1, escalation = 1), firing("later", day = 1, escalation = 3)),
            rawById = mapOf("first" to raw("first"), "later" to raw("later")),
            actorUuid = "A",
            currentDay = 1,
        )
        val byId = ctx.days[0].rows.associateBy { it.scheduleId }
        assertNull(byId["first"]?.escalationLabel)
        assertTrue(byId["later"]?.escalationLabel != null)
    }

    @Test
    fun allButtonsOnlyWhenMoreThanOneFiring() {
        val one = buildPressureDigestContext(
            listOf(firing("a", 1)), mapOf("a" to raw("a")), "A", 1,
        )
        val two = buildPressureDigestContext(
            listOf(firing("a", 1), firing("b", 1)), mapOf("a" to raw("a"), "b" to raw("b")), "A", 1,
        )
        assertEquals(false, one.showAllButtons)
        assertEquals(true, two.showAllButtons)
    }

    @Test
    fun pendingMeansFiredMoreRecentlyThanHandled() {
        val rows = arrayOf(
            raw("never-fired"),
            raw("fresh", lastFiredDay = 5),
            raw("handled", lastFiredDay = 5, lastHandledDay = 5),
            raw("refired", lastFiredDay = 9, lastHandledDay = 5),
        )
        assertEquals(listOf("fresh", "refired"), pendingPressureRows(rows).map { it.id })
        assertTrue(pendingPressureRows(null).isEmpty())
        assertTrue(pendingPressureRows(emptyArray()).isEmpty())
    }
}
