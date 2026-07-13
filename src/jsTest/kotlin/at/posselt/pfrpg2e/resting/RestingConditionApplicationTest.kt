package at.posselt.pfrpg2e.resting

import com.foundryvtt.pf2e.actor.PF2EActor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestingConditionApplicationTest {

    @Test
    fun testApplyConditionsValuedVsBinary() {
        val mockActor = js("""({
            increasedConditions: [],
            toggledConditions: [],
            increaseCondition: function(cond) {
                this.increasedConditions.push(cond);
                return Promise.resolve();
            },
            toggleCondition: function(cond, opts) {
                this.toggledConditions.push({ cond: cond, opts: opts });
                return Promise.resolve();
            }
        })""").unsafeCast<PF2EActor>()

        val conditions = arrayOf("unconscious", "prone", "frightened", "sickened")
        applyEncounterConditions(mockActor, conditions)

        val increased = mockActor.asDynamic().increasedConditions.unsafeCast<Array<String>>()
        val toggled = mockActor.asDynamic().toggledConditions.unsafeCast<Array<dynamic>>()

        // Valued conditions "frightened" and "sickened" should be increased
        assertEquals(2, increased.size)
        assertTrue(increased.contains("frightened"))
        assertTrue(increased.contains("sickened"))

        // Binary conditions "unconscious" and "prone" should be toggled
        assertEquals(2, toggled.size)
        assertEquals("unconscious", toggled[0].cond)
        assertTrue(toggled[0].opts.active == true)
        assertEquals("prone", toggled[1].cond)
        assertTrue(toggled[1].opts.active == true)
    }
}
