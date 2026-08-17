package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.ChosenFeat
import at.posselt.pfrpg2e.kingdom.data.getChosenFeatures
import at.posselt.pfrpg2e.kingdom.getExplodedFeatures
import at.posselt.pfrpg2e.kingdom.unrest.calculateUnrest
import at.posselt.pfrpg2e.kingdom.vacancies
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.roll
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject
import kotlin.math.abs
import kotlin.math.min
import at.posselt.pfrpg2e.kingdom.envyIgnoresIncrease
import at.posselt.pfrpg2e.kingdom.hasEnvyOfTheWorld

@Suppress("unused")
@JsPlainObject
external interface ChatUnrestContext {
    val overcrowded: Int
    val secondary: Int
    val atWar: Int
    val rulerVacant: Int
    val total: Int
}

fun calculateAnarchy(feats: List<ChosenFeat>) =
    feats.sumOf { it.feat.increaseAnarchyLimit ?: 0 } + 20

suspend fun adjustUnrest(
    kingdom: KingdomData,
    settlements: List<Settlement>,
    chosenFeats: List<ChosenFeat>,
    suppressChat: Boolean = false,
): Int {
    val chosenFeatures = kingdom.getChosenFeatures(kingdom.getExplodedFeatures())
    val unrest = calculateUnrest(kingdom.atWar, settlements, kingdom.vacancies(
        choices = chosenFeatures,
        bonusFeats = kingdom.bonusFeats,
        government = kingdom.government,
    ))
    val ruler = if (unrest.rulerVacant) roll(formula = "1d4", flavor = t("kingdom.rulerVacantGainingUnrest"), toChat = !suppressChat) else 0
    val newUnrest = unrest.war + unrest.secondaryTerritory + unrest.overcrowded + ruler
    // Envy of the World ignores the FIRST Unrest or Ruin increase of a Kingdom turn. The old check
    // was `kingdom.level >= 20`, which ignored EVERY Upkeep unrest increase, every turn, forever --
    // both more generous than the feature's text and blind to whether the feature was even taken.
    val envyIgnores = kingdom.hasEnvyOfTheWorld() &&
        envyIgnoresIncrease(newUnrest, kingdom.envyOfTheWorldFirstIgnoreUsed == true)
    return if (envyIgnores) {
        kingdom.envyOfTheWorldFirstIgnoreUsed = true
        if (!suppressChat) {
            postChatMessage(t("kingdom.ignoringUnrestIncrease"))
        }
        kingdom.unrest
    } else {
        if (!suppressChat) {
            postChatTemplate(
                templatePath = "chatmessages/unrest.hbs",
                templateContext = ChatUnrestContext(
                    overcrowded = unrest.overcrowded,
                    secondary = unrest.secondaryTerritory,
                    atWar = unrest.war,
                    rulerVacant = ruler,
                    total = newUnrest,
                ),
            )
        }
        val totalUnrest = newUnrest + kingdom.unrest
        if (totalUnrest >= 10) {
            roll(formula = "1d10", flavor = t("kingdom.gainingPointsToRuins"), toChat = !suppressChat)
            if (roll(formula = "1d20", flavor = t("kingdom.losingHexOnFlatCheck"), toChat = !suppressChat) >= 11) {
                if (!suppressChat) {
                    postChatMessage(t("kingdom.loseHexOfChoice"))
                }
            }
        }
        val anarchyAt = calculateAnarchy(chosenFeats)
        if (totalUnrest >= anarchyAt) {
            if (!suppressChat) {
                postChatMessage(t("kingdom.fallsIntoAnarchy"))
            }
        }
        min(anarchyAt, totalUnrest)
    }
}

suspend fun KingdomData.addUnrest(amount: Int, chosenFeats: List<ChosenFeat>): Int {
    val anarchyAt = calculateAnarchy(chosenFeats)
    val result = (unrest + amount).coerceIn(0, anarchyAt)
    val difference = result - unrest
    if (difference > 0) {
        postChatMessage(t("kingdom.gainingNUnrest", recordOf("count" to difference)))
    } else if (difference < 0) {
        postChatMessage(t("kingdom.losingNUnrest", recordOf("count" to abs(difference))))
    } else {
        postChatMessage(t("kingdom.already0Unrest"))
    }
    return result
}