package at.posselt.pfrpg2e.questevent

data class QuestGenerationResult(
    val sourceEventId: String,
    val sourceEventName: String,
    val preview: QuestTemplate,
    val isEligible: Boolean,
    val ineligibilityReason: String? = null,
)

object QuestGenerator {

    fun generateFromEvent(
        event: KingdomEventTemplate,
        kingdomLevel: Int,
        currentQuests: List<CampaignQuest>,
        settings: QuestGeneratorSettings = QuestGeneratorSettings(),
    ): QuestGenerationResult? {
        val activeGeneratedCount = currentQuests.count {
            it.status == QuestStatus.ACTIVE && it.generatedByEvent
        }
        if (activeGeneratedCount >= settings.maxActiveGeneratedQuests) {
            return QuestGenerationResult(
                sourceEventId = event.id,
                sourceEventName = event.name,
                preview = QuestTemplate(
                    id = "",
                    name = "",
                    type = QuestType.EXPLORATION,
                    description = "",
                    recommendedLevel = kingdomLevel,
                    objectives = emptyList(),
                    rewards = QuestRewards(),
                ),
                isEligible = false,
                ineligibilityReason = "Maximum active generated quests (${settings.maxActiveGeneratedQuests}) reached",
            )
        }

        val questType = determineQuestType(event.traits)
        val rewards = determineRewards(event.traits, kingdomLevel)
        val level = kingdomLevel.coerceAtLeast(1)

        return QuestGenerationResult(
            sourceEventId = event.id,
            sourceEventName = event.name,
            preview = QuestTemplate(
                id = "qt-${event.id}",
                name = "Respond to: ${event.name}",
                type = questType,
                description = event.description,
                recommendedLevel = level,
                objectives = listOf(
                    QuestObjective(
                        id = "${event.id}-obj-1",
                        description = "Investigate the ${event.name.lowercase()} situation",
                    ),
                    QuestObjective(
                        id = "${event.id}-obj-2",
                        description = "Resolve or mitigate the ${event.name.lowercase()}",
                        optional = false,
                    ),
                ),
                rewards = rewards,
                sourceEventTraits = event.traits,
                isDefaultVisibleToPlayers = false,
            ),
            isEligible = true,
        )
    }

    fun advanceQuestTimers(
        quests: List<CampaignQuest>,
        kingdomLevel: Int,
    ): List<CampaignQuest> = quests.map { quest ->
        if (quest.status != QuestStatus.ACTIVE || !quest.generatedByEvent) {
            quest
        } else {
            val remaining = quest.turnsRemaining
            if (remaining != null && remaining > 0) {
                val newRemaining = remaining - 1
                if (newRemaining <= 0) {
                    quest.copy(status = QuestStatus.FAILED, turnsRemaining = 0)
                } else {
                    quest.copy(turnsRemaining = newRemaining)
                }
            } else {
                quest
            }
        }
    }

    fun canGenerateMore(
        currentQuests: List<CampaignQuest>,
        settings: QuestGeneratorSettings,
    ): Boolean = currentQuests.count {
        it.status == QuestStatus.ACTIVE && it.generatedByEvent
    } < settings.maxActiveGeneratedQuests

    // ── Private mapping logic ────────────────────────────────────────────

    private fun determineQuestType(traits: List<String>): QuestType {
        val traitSet = traits.map { it.lowercase() }.toSet()
        // Priority: military > supernatural > political > agriculture > positive
        if (traitSet.any { it.contains("military") || it.contains("army") || it.contains("attack") }) {
            return QuestType.COMBAT
        }
        if (traitSet.any { it.contains("supernatural") || it.contains("magic") || it.contains("arcane") }) {
            return QuestType.EXPLORATION
        }
        if (traitSet.any { it.contains("political") || it.contains("diplomacy") || it.contains("treaty") }) {
            return QuestType.POLITICAL
        }
        if (traitSet.any { it.contains("agriculture") || it.contains("farm") || it.contains("crop") }) {
            return QuestType.EXPLORATION
        }
        if (traitSet.any { it.contains("positive") || it.contains("good") || it.contains("festival") }) {
            return QuestType.RP
        }
        return QuestType.EXPLORATION
    }

    private fun determineRewards(traits: List<String>, kingdomLevel: Int): QuestRewards {
        val traitSet = traits.map { it.lowercase() }.toSet()
        val baseXp = kingdomLevel * 10
        val baseRp = kingdomLevel * 2

        if (traitSet.any { it.contains("military") || it.contains("army") || it.contains("threat") }) {
            return QuestRewards(xp = baseXp + 20, rp = baseRp + 5)
        }
        if (traitSet.any { it.contains("political") || it.contains("diplomacy") }) {
            return QuestRewards(rp = baseRp + 8, fame = 1)
        }
        if (traitSet.any { it.contains("supernatural") }) {
            return QuestRewards(xp = baseXp + 30, rp = baseRp + 3)
        }
        if (traitSet.any { it.contains("agriculture") }) {
            return QuestRewards(rp = baseRp + 4, commodities = mapOf("food" to kingdomLevel))
        }
        if (traitSet.any { it.contains("positive") }) {
            return QuestRewards(fame = 1, rp = baseRp + 2)
        }
        return QuestRewards(xp = baseXp, rp = baseRp)
    }
}
