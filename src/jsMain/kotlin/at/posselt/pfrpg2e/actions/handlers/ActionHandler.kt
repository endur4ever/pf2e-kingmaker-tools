package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.actions.ActionDispatcher

enum class ExecutionMode {
    ALL,
    GM_ONLY,
    OTHERS,
}

enum class OriginatorPolicy {
    GM_ONLY,
    ANY,
}

abstract class ActionHandler(
    val action: String,
    val mode: ExecutionMode = ExecutionMode.GM_ONLY,
    // Deny-by-default: a new handler is GM-only-originable unless it explicitly opts into ANY.
    // This keeps the socket-authorization control fail-CLOSED so a newly-added GM-side mutation
    // can't silently ship player-originable (see t_000fd424 / the syncBattleOutcome regression).
    val originatorPolicy: OriginatorPolicy = OriginatorPolicy.GM_ONLY,
) {
    fun canExecute(action: ActionMessage): Boolean = action.action == this.action
    abstract suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher)
}