package at.posselt.pfrpg2e.camping

/**
 * A rumor surfaced by a RUMOR-category encounter (roadmap #11). A rumor flagged
 * as a quest hook can be converted into a [at.posselt.pfrpg2e.kingdom.CampaignQuest]
 * record; [isConverted]/[convertedQuestId] track that conversion.
 */
data class Rumor(
    val text: String,
    val isQuestHook: Boolean = false,
    val questTemplateId: String? = null,
    val questTemplateName: String? = null,
    val location: String? = null,
    val sourceRegion: String? = null,
    val isConverted: Boolean = false,
    val convertedQuestId: String? = null,
)
