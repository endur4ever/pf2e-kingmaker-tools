package at.posselt.pfrpg2e.camping

/**
 * A lightweight reference to a quest template that a rumor quest hook can spawn
 * from (roadmap #11). The full quest generator (roadmap #2) is out of scope here.
 */
data class QuestTemplateReference(
    val templateId: String,
    val name: String,
    val sourceEventTraits: List<String> = emptyList(),
    val recommendedLevel: Int = 1,
)
