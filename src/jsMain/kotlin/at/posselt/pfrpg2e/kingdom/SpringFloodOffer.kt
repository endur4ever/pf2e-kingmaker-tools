package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import js.objects.recordOf

/** The seasonal economy's single offer card (plan section 5.1), whispered to GMs. */
suspend fun postSpringFloodOffer(game: Game, actorUuid: String) {
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    // an EMPTY whisper array posts publicly, not to nobody
    if (gmUserIds.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/seasonal-flood-offer.hbs",
        templateContext = recordOf<String, Any?>("actorUuid" to actorUuid),
        whisper = gmUserIds,
    )
}

/** The kingdom event the offer spawns; authored as data, so its absence is reported, not crashed on. */
const val SPRING_FLOOD_EVENT_ID = "spring-flood"
