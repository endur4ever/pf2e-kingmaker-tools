package at.posselt.pfrpg2e.questevent

data class QuestGeneratorSettings(
    val defaultVisibilityToPlayers: Boolean = false,
    val maxActiveGeneratedQuests: Int = 10,
    val autoAdvanceQuestTimersOnTurn: Boolean = true,
)
