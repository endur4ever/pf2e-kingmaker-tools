package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.settlementlife.LifeEventHookKind
import at.posselt.pfrpg2e.kingdom.structures.RawSettlement
import at.posselt.pfrpg2e.kingdom.structures.carrySettlementEngineState
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettlementLifeParseTest {
    private fun raw(kind: String, magnitude: Int?) = unsafeJso<dynamic> {
        id = "synthetic"; name = "x"; gazette = "x"; category = "x"; baseWeight = 1
        hook = unsafeJso<dynamic> { this.kind = kind; this.magnitude = magnitude }
    }.unsafeCast<RawSettlementLifeEvent>()

    @Test
    fun anUnrestMagnitudeIsClampedToOnePointEitherWayAtParse() {
        // section 5.2: the button says "+1 Unrest"; a template claiming 10 must not make the
        // click do something the card never promised
        assertEquals(1, raw("unrest-delta", 10).toLifeTemplate().hookMagnitude)
        assertEquals(-1, raw("unrest-delta", -7).toLifeTemplate().hookMagnitude)
        // a missing or zero magnitude on an unrest hook is a hook that does nothing behind a
        // button that promises something: it defaults to +1, not to a no-op
        assertEquals(1, raw("unrest-delta", null).toLifeTemplate().hookMagnitude)
        assertEquals(1, raw("unrest-delta", 0).toLifeTemplate().hookMagnitude)
    }

    @Test
    fun anRpMagnitudeIsClampedToZeroOrOne() {
        assertEquals(1, raw("rp-delta", 5).toLifeTemplate().hookMagnitude)
        assertEquals(0, raw("rp-delta", -3).toLifeTemplate().hookMagnitude)
        assertEquals(1, raw("rp-delta", null).toLifeTemplate().hookMagnitude)
    }

    @Test
    fun flavorOnlyAndUnknownHooksCarryNoMagnitude() {
        assertEquals(0, raw("none", 4).toLifeTemplate().hookMagnitude)
        val unknown = raw("teleport-everyone", 4).toLifeTemplate()
        assertEquals(LifeEventHookKind.NONE, unknown.hookKind)
        assertEquals(0, unknown.hookMagnitude)
    }

    @Test
    fun aDialogSaveCannotRevertWhatTheTickWroteWhileItWasOpen() {
        val record = unsafeJso<dynamic> { recordId = "life-a-3-market-day"; templateId = "market-day"; turn = 3
            castNpcIds = emptyArray<String>(); castNames = arrayOf("x"); hookKind = "rp-delta"; hookMagnitude = 1; hookApplied = false }
        val live = unsafeJso<dynamic> { sceneId = "a"; lifeEventHistory = arrayOf(record); destroyedStructureIds = arrayOf("t1") }
            .unsafeCast<RawSettlement>()
        val submitted = unsafeJso<dynamic> { sceneId = "a"; lifeEventHistory = null; destroyedStructureIds = null; lots = 4 }
            .unsafeCast<RawSettlement>()
        val merged = carrySettlementEngineState(live, submitted)
        assertEquals(1, merged.lifeEventHistory?.size)
        assertEquals("t1", merged.destroyedStructureIds?.single())
        // and the dialog's own edits survive
        assertEquals(4, merged.lots)
    }

    @Test
    fun aBrandNewSettlementHasNothingToCarry() {
        val submitted = unsafeJso<dynamic> { sceneId = "new"; lifeEventHistory = null }.unsafeCast<RawSettlement>()
        assertNull(carrySettlementEngineState(null, submitted).lifeEventHistory)
    }
}
