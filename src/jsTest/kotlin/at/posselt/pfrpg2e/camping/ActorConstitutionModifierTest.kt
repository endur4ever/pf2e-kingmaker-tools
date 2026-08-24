package at.posselt.pfrpg2e.camping

import com.foundryvtt.pf2e.actor.PF2EActor
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Starvation thresholds scale with Constitution, so an actor whose Con is not read starves early.
 * The typed facade exposes abilities only on PF2ECharacter, which silently made every NPC camper
 * Con 0.
 */
class ActorConstitutionModifierTest {
    private fun actorWithSystemCon(conMod: Int): PF2EActor {
        val actor = unsafeJso<dynamic>()
        actor.system = unsafeJso<dynamic>()
        actor.system.abilities = unsafeJso<dynamic>()
        actor.system.abilities.con = unsafeJso<dynamic>()
        actor.system.abilities.con.mod = conMod
        return actor.unsafeCast<PF2EActor>()
    }

    @Test
    fun anNpcCamperKeepsItsConstitution() {
        assertEquals(3, actorConstitutionModifier(actorWithSystemCon(3)))
    }

    @Test
    fun aNegativeConstitutionIsReadAsNegative() {
        // Falling back to 0 for a frail NPC would make them starve LATER, not earlier.
        assertEquals(-1, actorConstitutionModifier(actorWithSystemCon(-1)))
    }

    @Test
    fun anActorWithNoAbilitiesBlockIsZero() {
        // A vehicle has no Constitution; 0 is correct rather than a guess.
        val vehicle = unsafeJso<dynamic> { system = unsafeJso<dynamic> {} }.unsafeCast<PF2EActor>()
        assertEquals(0, actorConstitutionModifier(vehicle))
    }

    @Test
    fun anActorWithNoSystemDataAtAllIsZeroRatherThanThrowing() {
        assertEquals(0, actorConstitutionModifier(unsafeJso<dynamic> {}.unsafeCast<PF2EActor>()))
    }
}
