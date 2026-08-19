package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.data.actor.isValuedCondition
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.utils.fromUuid
import com.foundryvtt.pf2e.actor.PF2EActor
import js.objects.recordOf
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface ApplyStarvation {
    val actorUuid: String
    val condition: String
}

/**
 * Applies a starvation condition to one camper, on the GM's explicit confirmation.
 *
 * Starvation is never auto-applied: the nightly tick only counts nights and whispers an offer card
 * to the GM, and this runs when they press the button. Inherits [ActionHandler]'s deny-by-default
 * originator policy, so a player cannot dispatch it even by crafting the socket message.
 *
 * Re-clicking a card left in chat scrollback is safe: `fatigued` is a binary condition, so the
 * `hasCondition` guard makes a second press a no-op rather than stacking.
 */
class ApplyStarvationHandler : ActionHandler("applyStarvation") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<ApplyStarvation>()
        val actor = fromUuid(data.actorUuid).await().unsafeCast<PF2EActor?>() ?: return
        val condition = data.condition
        if (isValuedCondition(condition)) {
            actor.increaseCondition(condition)
        } else if (!actor.hasCondition(condition)) {
            actor.toggleCondition(condition)
        }
        postChatMessage(
            t("camping.starvationApplied", recordOf("name" to (actor.name ?: ""))),
            speaker = actor,
        )
    }
}
