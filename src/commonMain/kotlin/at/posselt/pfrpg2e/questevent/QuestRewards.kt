package at.posselt.pfrpg2e.questevent

data class QuestRewards(
    val xp: Int = 0,
    val rp: Int = 0,
    val fame: Int = 0,
    val commodities: Map<String, Int> = emptyMap(),
    val unrestReduction: Int = 0,
    val structureAccessGranted: String? = null,
    val customReward: String? = null,
)
