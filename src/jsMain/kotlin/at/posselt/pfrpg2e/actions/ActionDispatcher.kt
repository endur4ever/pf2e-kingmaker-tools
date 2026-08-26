package at.posselt.pfrpg2e.actions

import at.posselt.pfrpg2e.actions.handlers.ActionHandler
import at.posselt.pfrpg2e.actions.handlers.ExecutionMode
import at.posselt.pfrpg2e.actions.handlers.OriginatorPolicy
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.emitPfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.isFirstGM
import at.posselt.pfrpg2e.utils.isJsObject
import at.posselt.pfrpg2e.utils.onPfrpg2eKingdomCampingWeather
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game

class ActionDispatcher(
    val game: Game,
    val handlers: List<ActionHandler>,
    val debug: Boolean = true,
) {
    fun listen() {
        game.socket.onPfrpg2eKingdomCampingWeather { message ->
            if (debug) console.log("Received Socket Message", message)
            if (isJsObject(message)) {
                val action = message["action"]
                val data = message["data"]
                if (action is String && isJsObject(data)) {
                    buildPromise {
                        dispatch(message.unsafeCast<ActionMessage>(), true)
                    }
                }
            }
        }
    }

    suspend fun dispatch(action: ActionMessage, receivedViaSocket: Boolean = false) {
        if (debug) console.log("Dispatching action", action)
        val handler = handlers.find { it.canExecute(action) }
        if (handler != null) {
            if (!receivedViaSocket) {
                // Stamp the local sender BEFORE any branch, not just on the emit paths: the
                // local-execute path (first-GM client) used to reach handlers with senderId
                // undefined, so a handler that keys on it -- council ballots -- silently
                // dropped the first GM's own action. The emit branches below re-stamp the same
                // value, which is harmless.
                action.asDynamic().senderId = game.user._id
            }
            val senderId = action.senderId

            // Note: Since this is client-side code running in a browser, a player
            // could modify their client-side memory or local javascript payload to forge
            // the senderId metadata (e.g. setting it to a GM's user ID). This is a residual
            // trust limitation of a peer-to-peer/broadcast client-side module architecture.
            val senderUser = senderId?.let { game.users.get(it) }
            val isSenderGm = senderUser?.isGM == true

            if (handler.originatorPolicy == OriginatorPolicy.GM_ONLY && !isSenderGm) {
                console.warn("Rejected action '${action.action}': Sender '${senderUser?.name ?: "Unknown"}' (ID: $senderId) is not a GM.")
                return
            }

            if (handler.mode == ExecutionMode.GM_ONLY && game.isFirstGM()) {
                handler.execute(action, this)
            } else if (handler.mode == ExecutionMode.GM_ONLY && !game.isFirstGM() && !receivedViaSocket) {
                action.asDynamic().senderId = game.user._id
                game.socket.emitPfrpg2eKingdomCampingWeather(action.unsafeCast<AnyObject>())
            } else if (handler.mode == ExecutionMode.OTHERS) {
                // break endless socket emitting circuit
                if (!receivedViaSocket) {
                    action.asDynamic().senderId = game.user._id
                    game.socket.emitPfrpg2eKingdomCampingWeather(action.unsafeCast<AnyObject>())
                } else {
                    handler.execute(action, this)
                }
            } else if (handler.mode == ExecutionMode.ALL) {
                handler.execute(action, this)
                // break endless socket emitting circuit
                if (!receivedViaSocket) {
                    action.asDynamic().senderId = game.user._id
                    game.socket.emitPfrpg2eKingdomCampingWeather(action.unsafeCast<AnyObject>())
                }
            }
        } else {
            if (debug) console.log("No handler found for action", action)
        }
    }
}