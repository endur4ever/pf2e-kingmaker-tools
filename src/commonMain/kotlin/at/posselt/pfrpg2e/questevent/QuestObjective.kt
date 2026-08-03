package at.posselt.pfrpg2e.questevent

data class QuestObjective(
    val id: String,
    val description: String,
    val completed: Boolean = false,
    val optional: Boolean = false,
)
