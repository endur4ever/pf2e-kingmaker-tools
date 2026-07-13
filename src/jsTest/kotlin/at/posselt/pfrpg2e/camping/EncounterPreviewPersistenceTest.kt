package at.posselt.pfrpg2e.camping

import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

class EncounterPreviewPersistenceTest {

    private fun runTest(block: suspend () -> Unit): dynamic = @Suppress("DELICATE_API_TRANSITIONAL_MINI_MARKER") GlobalScope.promise { block() }

    @Test
    fun testEncounterPreviewFieldsReadWrite() {
        val camping = unsafeJso<CampingData> {
            lastEncounterCategory = "combat"
            lastEncounterResult = "3 Orcs attack!"
        }

        assertEquals("combat", camping.lastEncounterCategory)
        assertEquals("3 Orcs attack!", camping.lastEncounterResult)

        camping.lastEncounterCategory = null
        camping.lastEncounterResult = null

        assertNull(camping.lastEncounterCategory)
        assertNull(camping.lastEncounterResult)
    }

    @Test
    fun testSaveAndClearHelpers() = runTest {

        val camping = unsafeJso<CampingData> {
            lastEncounterCategory = null
            lastEncounterResult = null
        }

        val mockActor = js("""({
            camping: null,
            getFlag: function(scope, key) {
                if (key === 'camping-sheet') return this.camping;
                return null;
            },
            setFlag: function(scope, key, value) {
                if (key === 'camping-sheet') {
                    this.camping = value;
                }
                return Promise.resolve(this);
            }
        })""").unsafeCast<CampingActor>()

        mockActor.asDynamic().camping = camping

        saveEncounterPreviewFields(mockActor, "rp", "A mysterious stranger approaches.")

        val savedCamping = mockActor.getCamping()
        assertEquals("rp", savedCamping?.lastEncounterCategory)
        assertEquals("A mysterious stranger approaches.", savedCamping?.lastEncounterResult)

        clearEncounterPreviewFields(mockActor)

        val clearedCamping = mockActor.getCamping()
        assertNull(clearedCamping?.lastEncounterCategory)
        assertNull(clearedCamping?.lastEncounterResult)
    }
}
