package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import com.foundryvtt.core.Game
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2ENpc

fun Game.getActiveLeader(): Leader? =
    settings.pfrpg2eKingdomCampingWeather
        .getKingdomActiveLeader()
        ?.let { Leader.fromString(it) }

suspend fun getOwnedLeaderRoles(game: Game, kingdom: KingdomData): Set<Leader> {
    if (game.user.isGM) return Leader.entries.toSet()
    val leaders = kingdom.leaders
    val roles = listOf(
        Leader.RULER to leaders.ruler,
        Leader.COUNSELOR to leaders.counselor,
        Leader.EMISSARY to leaders.emissary,
        Leader.GENERAL to leaders.general,
        Leader.MAGISTER to leaders.magister,
        Leader.TREASURER to leaders.treasurer,
        Leader.VICEROY to leaders.viceroy,
        Leader.WARDEN to leaders.warden,
    )
    val owned = mutableSetOf<Leader>()
    for ((role, valObj) in roles) {
        val uuid = valObj.uuid
        if (uuid != null) {
            val actor = fromUuidOfTypes(
                uuid,
                PF2ECharacter::class,
                PF2ENpc::class
            )
            if (actor != null && actor.isOwner) {
                owned.add(role)
            }
        }
    }
    return owned
}

