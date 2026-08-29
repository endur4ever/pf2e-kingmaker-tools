package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.AgendaFactionMove
import at.posselt.pfrpg2e.data.kingdom.AgendaMoveEffect
import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * The per-turn faction-moves digest (plan sections 6.1-6.2): ONE whispered card per turn, one
 * row per move, so five factions never spam five cards. War-threat and diplomacy-quest buttons
 * REUSE the existing km-offer-war-threat / km-offer-diplomacy-quest handlers -- identical data
 * contract, so the plan's dedicated handler names would have duplicated tested code; the only
 * new handler is the standing-shift confirm.
 */

/** Literal keys: the reason lands in the standing log and renders through t() there. */
private fun shiftReasonKey(moveId: String): String = when (moveId) {
    "sabotage-rival" -> "kingdom.factionAgenda.shiftReason.sabotageRival"
    "court-ally" -> "kingdom.factionAgenda.shiftReason.courtAlly"
    "court-pcs" -> "kingdom.factionAgenda.shiftReason.courtPcs"
    else -> "kingdom.factionAgenda.shiftReason.other"
}

suspend fun postFactionMoveDigest(
    game: Game,
    actorUuid: String,
    currentTurn: Int,
    moves: List<AgendaFactionMove>,
) {
    if (moves.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    val rows = moves.map { move ->
        val row = recordOf<String, Any?>(
            "line" to localizeAgendaMoveLine(move),
            "goalCompleted" to move.goalCompleted,
        )
        when (val effect = move.effect) {
            is AgendaMoveEffect.ClockSegments -> {}
            is AgendaMoveEffect.StandingDelta -> {
                row["shiftTarget"] = effect.targetFaction
                row["shiftDelta"] = effect.delta
                row["shiftReason"] = shiftReasonKey(move.moveId)
                if (effect.offerWarThreat) row["warThreatFaction"] = effect.targetFaction
                if (effect.offerDiplomacyQuest) row["questFaction"] = effect.targetFaction
            }
            is AgendaMoveEffect.CourtPcs -> {
                // open question 3, resolved by the data model: RawGroup.standing IS the
                // PC-facing relation, so courting the PCs warms the ACTING faction's standing
                row["shiftTarget"] = move.factionName
                row["shiftDelta"] = effect.delta
                row["shiftReason"] = shiftReasonKey(move.moveId)
                if (effect.offerDiplomacyQuest) row["questFaction"] = move.factionName
            }
            is AgendaMoveEffect.ArmyRaised -> {
                row["warThreatFaction"] = move.factionName
            }
        }
        row
    }.toTypedArray()
    postChatTemplate(
        templatePath = "chatmessages/faction-moves-digest.hbs",
        templateContext = recordOf<String, Any?>(
            "actorUuid" to actorUuid,
            "turn" to currentTurn,
            "moves" to rows,
        ),
        whisper = gmUserIds,
    )
}
