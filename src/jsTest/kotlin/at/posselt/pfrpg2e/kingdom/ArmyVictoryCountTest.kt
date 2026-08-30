package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.armies.BattleStatus
import js.objects.unsafeJso
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The first-battle-won deed is LEVEL-triggered, so its input must survive archiving. Before
 * archivedOutcome existed the tick overwrote status and the count dropped to zero the turn after
 * a win -- so a GM who ignored one digest lost that deed forever.
 */
class ArmyVictoryCountTest {
    private fun battle(status: String, archivedOutcome: String? = null) = unsafeJso<dynamic> {
        this.id = "b"
        this.status = status
        this.archivedOutcome = archivedOutcome
    }

    private fun kingdomWith(vararg battles: dynamic): KingdomData {
        val k = unsafeJso<dynamic> {}
        k.activeBattles = arrayOf(*battles)
        return k.unsafeCast<KingdomData>()
    }

    @Test
    fun aLiveVictoryCounts() {
        assertEquals(1, countArmyVictories(kingdomWith(battle(BattleStatus.VICTORY.value))))
    }

    @Test
    fun anArchivedVictoryStillCounts() {
        // this is the whole point: status is "archived", the outcome is remembered separately
        assertEquals(1, countArmyVictories(kingdomWith(battle("archived", BattleStatus.VICTORY.value))))
    }

    @Test
    fun defeatsAndActiveBattlesDoNotCount() {
        assertEquals(
            0,
            countArmyVictories(
                kingdomWith(
                    battle(BattleStatus.ACTIVE.value),
                    battle("archived", BattleStatus.DEFEAT.value),
                    // a battle archived before the field existed reports no outcome
                    battle("archived", null),
                )
            ),
        )
    }

    @Test
    fun victoriesAccumulateAcrossTurns() {
        assertEquals(
            3,
            countArmyVictories(
                kingdomWith(
                    battle("archived", BattleStatus.VICTORY.value),
                    battle("archived", BattleStatus.VICTORY.value),
                    battle(BattleStatus.VICTORY.value),
                )
            ),
        )
    }

    @Test
    fun noBattlesAtAllIsZero() {
        val k = unsafeJso<dynamic> {}
        assertEquals(0, countArmyVictories(k.unsafeCast<KingdomData>()))
    }
}
