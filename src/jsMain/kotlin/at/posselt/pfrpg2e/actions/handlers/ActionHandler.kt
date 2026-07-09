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
    val originatorPolicy: OriginatorPolicy = OriginatorPolicy.ANY,
) {
    fun canExecute(action: ActionMessage): Boolean = action.action == this.action
    abstract suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher)
}