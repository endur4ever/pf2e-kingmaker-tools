package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Attitude-shift offers (plan section 7): edge-triggered on band CROSSINGS only, one whispered
 * card per crossing -- an NPC sitting at hostile re-offers nothing. The card names the NPC, the
 * settlement, the new band and the memories that moved them; the GM turns it into an encounter,
 * a quest, a note, or closes it.
 */
suspend fun postNpcAttitudeShiftOffers(
    game: Game,
    actorUuid: String,
    crossings: List<NpcBandCrossing>,
) {
    if (crossings.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    for (crossing in crossings) {
        val settlementName = game.scenes.find { it.id == crossing.settlementSceneId }?.name ?: ""
        postChatTemplate(
            templatePath = "chatmessages/npc-attitude-shift.hbs",
            templateContext = recordOf<String, Any?>(
                "actorUuid" to actorUuid,
                "npcName" to crossing.npcName,
                "occupation" to crossing.occupation,
                "settlementName" to settlementName,
                "bandLabel" to localizeAttitudeBand(crossing.band),
                "score" to crossing.score,
                "memories" to crossing.firedRuleIds.map { localizeMemoryEntry(it) }.toTypedArray(),
            ),
            whisper = gmUserIds,
        )
    }
}
