package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.kingdom.data.RawPcDowntimeProject
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeKind
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DowntimeSectionContextTest {
    private fun raw(
        id: String,
        status: String = "inProgress",
        pauseReason: String? = null,
        pcActorUuid: String = "Actor.a",
    ): RawPcDowntimeProject {
        val obj = js("{}").unsafeCast<RawPcDowntimeProject>()
        obj.id = id
        obj.pcActorUuid = pcActorUuid
        obj.kind = "craft"
        obj.targetRef = ""
        obj.title = "Project $id"
        obj.daysTotal = 10
        obj.daysRemaining = 3
        obj.status = status
        obj.pauseReason = pauseReason
        return obj
    }

    @Test
    fun rowsResolveNamesAndCountOnlyInProgressWork() {
        val ctx = buildDowntimeSectionContext(
            arrayOf(raw("a"), raw("b", status = "completed"), raw("c", status = "paused")),
        ) { uuid -> if (uuid == "Actor.a") "Amiri" else null }
        assertEquals(3, ctx.rows.size)
        assertEquals("Amiri", ctx.rows[0].pcName)
        assertEquals(1, ctx.activeCount, "only inProgress counts toward the badge")
        assertTrue(ctx.rows[1].completed)
        assertTrue(ctx.rows[2].paused)
    }

    @Test
    fun unresolvableActorFallsBackWithoutThrowing() {
        val ctx = buildDowntimeSectionContext(arrayOf(raw("a", pcActorUuid = "Actor.gone"))) { null }
        assertTrue(ctx.rows[0].pcName.isNotBlank(), "deleted PC still renders a row")
    }

    @Test
    fun prerequisitePauseGetsItsOwnLabel() {
        val ctx = buildDowntimeSectionContext(
            arrayOf(
                raw("plain", status = "paused"),
                raw("prereq", status = "paused", pauseReason = "prerequisiteLost"),
            ),
        ) { "X" }
        assertTrue(ctx.rows[0].statusLabel != ctx.rows[1].statusLabel, "structure-lost pause is distinguishable")
    }

    @Test
    fun daysProgressCarriesBothNumbers() {
        val ctx = buildDowntimeSectionContext(arrayOf(raw("a"))) { "X" }
        assertTrue(ctx.rows[0].daysLabel.contains("3") && ctx.rows[0].daysLabel.contains("10"))
    }

    @Test
    fun nullProjectsIsAnEmptySectionNotACrash() {
        val ctx = buildDowntimeSectionContext(null) { "X" }
        assertEquals(0, ctx.rows.size)
        assertEquals(0, ctx.activeCount)
    }

    private fun model(kind: DowntimeKind) = DowntimeProject(
        id = "p", pcActorUuid = "Actor.a", kind = kind, title = "T",
        daysTotal = 10, daysRemaining = 0,
    )

    @Test
    fun onlyCraftTargetsBecomeUuidLinks() {
        assertEquals(
            "@UUID[Item.abc]",
            downtimeCompleteContext("A", model(DowntimeKind.CRAFT), "Item.abc").targetLink,
        )
        assertNull(downtimeCompleteContext("A", model(DowntimeKind.RETRAIN), "Item.abc").targetLink)
        assertNull(downtimeCompleteContext("A", model(DowntimeKind.CRAFT), "").targetLink)
        assertNull(downtimeCompleteContext("A", model(DowntimeKind.CRAFT), null).targetLink)
    }
}
