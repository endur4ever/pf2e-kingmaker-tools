package at.posselt.pfrpg2e.questevent

data class EventGenerationLog(
    val id: String,
    val turnNumber: Int,
    val eventsGenerated: Int = 0,
    val questsCreated: Int = 0,
    val timestamp: String,
    val campaignId: String,
)
