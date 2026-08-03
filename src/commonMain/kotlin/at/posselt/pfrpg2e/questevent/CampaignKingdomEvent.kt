package at.posselt.pfrpg2e.questevent

data class CampaignKingdomEvent(
    val id: String,
    val eventTemplateId: String,
    val name: String,
    val description: String,
    val status: EventStatus = EventStatus.ACTIVE,
    val spawnedQuestIds: List<String> = emptyList(),
    val turnsActive: Int = 0,
    val createdAt: String,
    val resolvedAt: String? = null,
)

enum class EventStatus(val value: String) {
    ACTIVE("active"),
    RESOLVED("resolved"),
    EXPIRED("expired"),
    GENERATED_QUEST("generated_quest");

    companion object {
        fun fromString(value: String) = entries.firstOrNull { it.value == value }
    }
}
