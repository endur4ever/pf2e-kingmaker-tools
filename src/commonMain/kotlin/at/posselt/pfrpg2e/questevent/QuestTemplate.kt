package at.posselt.pfrpg2e.questevent

data class QuestTemplate(
    val id: String,
    val name: String,
    val type: QuestType,
    val description: String,
    val gmNotes: String? = null,
    val recommendedLevel: Int,
    val objectives: List<QuestObjective>,
    val rewards: QuestRewards,
    val sourceEventTraits: List<String> = emptyList(),
    val isDefaultVisibleToPlayers: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

enum class QuestType(val value: String) {
    COMBAT("combat"),
    EXPLORATION("exploration"),
    RP("roleplay"),
    POLITICAL("political"),
    CRAFTING("crafting"),
    TRAVEL("travel");

    companion object {
        fun fromString(value: String) = entries.firstOrNull { it.value == value }
    }
}
