package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import js.objects.recordOf

/** The Plague event a failed Irrigation flat check invites. */
const val PLAGUE_EVENT_ID = "plague"

/**
 * GM-confirmed offer to add the Plague event after a failed Irrigation flat check.
 *
 * An offer rather than an automatic spawn, matching every other consequence in this module: adding
 * an ongoing event is the kind of thing a GM wants to place in their own pacing.
 */
suspend fun postIrrigationPlagueOffer(game: Game, actor: KingdomActor) {
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/irrigation-plague-offer.hbs",
        templateContext = recordOf("actorUuid" to actor.uuid),
        whisper = gmUserIds,
    )
}
