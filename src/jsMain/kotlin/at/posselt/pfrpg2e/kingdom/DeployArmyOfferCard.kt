package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.setAppFlag
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.utils.fromUuid
import com.foundryvtt.pf2e.actor.PF2EArmy
import com.foundryvtt.pf2e.item.PF2EEffect
import js.objects.recordOf
import kotlinx.coroutines.await

/**
 * Compendium UUIDs of the army-condition Effect items this activity grants, from
 * `pf2e.kingmaker-features` (the PF2e system's own Army Conditions folder).
 *
 * Applying the real items rather than a module-local flag is what makes them mechanically live:
 * each carries the rule elements PF2e defines for it (weary penalises AC and maneuver checks by its
 * badge value), and the module's own check modifiers read those badges straight back off the actor.
 */
private val DEPLOY_EFFECT_UUIDS: Map<DeployArmyEffect, String> = mapOf(
    DeployArmyEffect.EFFICIENT to "Compendium.pf2e.kingmaker-features.Item.DURRMyANFnFccM24",
    DeployArmyEffect.WEARY to "Compendium.pf2e.kingmaker-features.Item.dmzpRlOrorEFUETl",
    DeployArmyEffect.LOST to "Compendium.pf2e.kingmaker-features.Item.Ei8EMUwVxUNyMQJJ",
)

fun deployEffectUuid(effect: DeployArmyEffect): String? = DEPLOY_EFFECT_UUIDS[effect]

/** Flag on the army actor recording which buttons of which cards have been applied. */
private const val DEPLOY_APPLIED_FLAG = "deployOutcomeApplied"

/**
 * How many "<cardId>|<key>" stamps to keep on an army.
 *
 * Bounded because a long campaign deploys the same army many times and the flag would otherwise
 * grow without limit. Thirty covers far more than the handful of cards a GM could plausibly still
 * have on screen, which is the only window in which a stale click is possible.
 */
private const val DEPLOY_APPLIED_HISTORY = 30

private fun stamp(cardId: String, key: String) = "$cardId|$key"

/** Keys already applied for [cardId] on this army. */
fun PF2EArmy.appliedDeployKeys(cardId: String): Set<String> =
    (getAppFlag<PF2EArmy, Array<String>>(DEPLOY_APPLIED_FLAG) ?: emptyArray())
        .filter { it.startsWith("$cardId|") }
        .map { it.substringAfter("|") }
        .toSet()

/** Record [key] as applied for [cardId], trimming the oldest stamps. */
suspend fun PF2EArmy.recordDeployKeyApplied(cardId: String, key: String) {
    val existing = getAppFlag<PF2EArmy, Array<String>>(DEPLOY_APPLIED_FLAG) ?: emptyArray()
    val updated = (existing + stamp(cardId, key)).takeLast(DEPLOY_APPLIED_HISTORY).toTypedArray()
    setAppFlag(DEPLOY_APPLIED_FLAG, updated)
}

/**
 * Put [effect] on this army: raise the badge if it is a valued condition already present, otherwise
 * create the compendium item.
 *
 * Returns false when the compendium item cannot be resolved — the pf2e system's Kingmaker packs are
 * not installed, or the id moved — so the caller can say so instead of reporting a silent success.
 */
suspend fun PF2EArmy.applyDeployEffect(effect: DeployArmyEffect): Boolean {
    val existing = itemTypes.effect.find { it.slug == effect.slug }
    if (existing != null) {
        // A non-valued condition is either on or off; re-applying it is a no-op, not a stack.
        if (!effect.valued) return true
        val current = existing.asDynamic().system?.badge?.value as? Int ?: 0
        val update = js("{}")
        update["system.badge.value"] = current + 1
        existing.update(update.unsafeCast<AnyObject>()).await()
        return true
    }
    val source = fromUuid(deployEffectUuid(effect) ?: return false).await() ?: return false
    val sourceData = source.asDynamic().toObject().unsafeCast<AnyObject>()
    createEmbeddedDocuments<PF2EEffect>("Item", arrayOf(sourceData)).await()
    return true
}

/**
 * Posts the GM-confirmed offer card for a resolved Deploy Army activity.
 *
 * The activity's own text ends with "HP and Conditions need to be managed by hand"; these buttons
 * are what replaces that. Nothing is applied here — each button applies exactly its own effect.
 *
 * [army] is the army token that was selected when the check was rolled, which is the same army
 * whose weary/mired badges modified the roll (see Game.getSelectedArmyConditions). Without a
 * selected army there is nothing to apply conditions to, so no card is posted.
 */
suspend fun offerDeployArmyOutcome(
    game: Game,
    actor: KingdomActor,
    army: PF2EArmy?,
    degree: DegreeOfSuccess,
    cardId: String,
) {
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    if (army == null) return

    val outcome = deployArmyOutcome(degree)
    val offers = deployArmyOffers(outcome, army.appliedDeployKeys(cardId))
    if (offers.isEmpty()) return

    val context = js("{}")
    context.actorUuid = actor.uuid
    context.armyActorUuid = army.uuid
    context.cardId = cardId
    context.armyName = army.name
    context.body = t("chatMessages.deployArmy.body", recordOf("army" to army.name))
    context.offers = offers.map { offer ->
        val row = js("{}")
        row.key = offer.key
        row.amount = offer.amount
        row.label = when (offer.key) {
            DEPLOY_OFFER_FLAT_CHECK -> t("chatMessages.deployArmy.flatCheck", recordOf("dc" to offer.amount))
            DEPLOY_OFFER_UNREST -> t("chatMessages.deployArmy.unrest", recordOf("dice" to (outcome.unrestDice ?: "")))
            else -> t(
                "chatMessages.deployArmy.condition",
                recordOf("condition" to t("armyConditionSlug.${offer.key}"), "army" to army.name),
            )
        }
        row
    }.toTypedArray()

    postChatTemplate(
        templatePath = "chatmessages/deploy-army-offer.hbs",
        templateContext = context,
        whisper = gmUserIds,
    )
}
