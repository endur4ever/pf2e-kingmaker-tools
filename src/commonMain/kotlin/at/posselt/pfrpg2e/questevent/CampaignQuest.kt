package at.posselt.pfrpg2e.questevent

data class CampaignQuest(
    val id: String,
    val templateId: String?,
    val name: String,
    val type: QuestType,
    val description: String,
    val gmNotes: String? = null,
    val recommendedLevel: Int,
    val objectives: List<QuestObjective>,
    val rewards: QuestRewards,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val turnsRemaining: Int? = null,
    val assignedPcIds: List<String> = emptyList(),
    val visibleToPlayers: Boolean = false,
    val generatedByEvent: Boolean = false,
    val sourceEventId: String? = null,
    val sourceEventName: String? = null,
    val createdAt: String,
    val completedAt: String? = null,
    val campaignId: String,
)

enum class QuestStatus(val value: String) {
    ACTIVE("active"),
    COMPLETED("completed"),
    FAILED("failed"),
    ABANDONED("abandoned"),
    ON_HOLD("on_hold");

    companion object {
        fun fromString(value: String) = entries.firstOrNull { it.value == value }
    }
}
