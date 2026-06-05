package at.posselt.pfrpg2e.questevent

data class KingdomEventTemplate(
    val id: String,
    val name: String,
    val description: String,
    val traits: List<String>,
    val triggerConditions: Map<String, Any> = emptyMap(),
    val suggestedQuestTemplateIds: List<String> = emptyList(),
    val consequenceOverrides: Map<String, Any> = emptyMap(),
)
