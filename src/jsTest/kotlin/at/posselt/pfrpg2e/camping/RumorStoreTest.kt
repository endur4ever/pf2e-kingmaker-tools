package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RumorStoreTest {
    private fun raw(state: String? = "fresh", id: String? = "r1"): RawRumor {
        val obj = js("{}").unsafeCast<RawRumor>()
        obj.text = "Troll sightings"
        obj.isQuestHook = true
        obj.isConverted = false
        obj.id = id
        obj.bornDay = 3
        obj.state = state
        obj.veracity = "distorted"
        obj.beatOfferedDay = null
        obj.sourceHexKey = "5.12"
        return obj
    }

    @Test
    fun theLifecycleFieldsSurviveTheRoundTrip() {
        val model = raw().toModel()!!
        assertEquals("r1", model.id)
        assertEquals(3, model.bornDay)
        assertEquals(RumorState.FRESH, model.state)
        assertEquals(RumorVeracity.DISTORTED, model.veracity)
        assertEquals("5.12", model.sourceHexKey)

        val back = model.toRaw()
        assertEquals("r1", back.id)
        assertEquals(3, back.bornDay)
        assertEquals("fresh", back.state)
        assertEquals("distorted", back.veracity)
        assertEquals("5.12", back.sourceHexKey)
    }

    @Test
    fun anUnknownStateDropsTheRowNotTheStore() {
        assertNull(raw(state = "haunted").toModel(),
            "a state this build does not know must not mis-age or throw")
        // but a LEGACY row with no state at all reads fresh
        assertEquals(RumorState.FRESH, raw(state = null).toModel()?.state)
    }

    @Test
    fun anUnknownVeracityDegradesToUnassessedRatherThanDroppingTheRow() {
        val obj = raw()
        obj.veracity = "maybe"
        val model = obj.toModel()!!
        assertNull(model.veracity, "veracity is advisory; a bad value must not cost the GM the rumor")
    }

    @Test
    fun aLegacyRowWithoutAnIdReadsAsBlankAndWritesBackAsNull() {
        val model = raw(id = null).toModel()!!
        assertEquals("", model.id)
        assertNull(model.toRaw().id, "blank must not be persisted as a fake identity")
    }

    @Test
    fun conversionRecordsTheQuestItBecame() {
        // convertedQuestId was declared, round-tripped, and never written by any path; the
        // convert callers now store it, so a CONVERTED row can name its quest
        val model = raw().toModel()!!.copy(
            state = RumorState.CONVERTED, isConverted = true, convertedQuestId = "quest-9",
        )
        assertEquals("quest-9", model.toRaw().convertedQuestId)
    }
}
