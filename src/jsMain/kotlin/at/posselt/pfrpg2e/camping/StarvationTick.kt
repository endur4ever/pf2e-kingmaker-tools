package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.utils.asSequence
import com.foundryvtt.core.Game
import com.foundryvtt.pf2e.actor.PF2EActor
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2EParty
import js.array.component1
import js.array.component2

/**
 * Advances every camper's hunger by one night, rolling Subsist for anyone who counted on rations
 * the party no longer has.
 *
 * Called from daily preparations while camping state is still in memory, so the counter rides the
 * one existing `setCamping` persist rather than adding a second write.
 *
 * See card t_40652699.
 */

/** A camper who crossed into a worse starvation band tonight — the trigger for the GM offer. */
data class StarvationCrossing(
    val actorUuid: String,
    val actorName: String,
    val daysWithoutFood: Int,
    val severity: StarvationSeverity,
)

/**
 * Constitution modifier for starvation thresholds.
 *
 * Only PF2e characters carry an abilities block; NPC and vehicle campers are legal in camp and
 * have none, so they fall back to 0 — `starvationThresholdDays(0) == 1`, the harshest threshold.
 * That is deliberate: an NPC follower with no Constitution to read should not get a *better*
 * endurance than the party, and a GM who disagrees can feed them.
 */
/**
 * Constitution modifier for starvation thresholds, 0 when the actor genuinely has none.
 *
 * The typed facade declares `abilities` only on [PF2ECharacter], but an NPC camper -- a companion
 * added to the party -- carries a real Constitution in system data. Reading only the typed path
 * treated every NPC as Con 0, so they starved on the same clock as a vehicle. Vehicles and other
 * actors with no abilities block still fall through to 0, which is correct for them.
 */
fun actorConstitutionModifier(actor: PF2EActor): Int {
    // Guarded SEPARATELY from the fallback below. A single runCatching around both would let a
    // throw from the typed check -- `as?` against an external class resolves through CONFIG.PF2E,
    // which is absent outside a live Foundry -- swallow the fallback too and silently yield 0.
    val typed = runCatching { (actor as? PF2ECharacter)?.abilities?.con?.mod }.getOrNull()
    if (typed != null) return typed
    return runCatching { actor.asDynamic().system?.abilities?.con?.mod as? Int }.getOrNull() ?: 0
}

/**
 * Resolves tonight's eating for [actors] and advances their hunger counters on [camping].
 *
 * MUTATES [camping] in place and does NOT persist — the caller's existing save covers it.
 * Returns the campers who crossed into a worse band, for the GM-confirmed offer.
 *
 * Note this reads the ration pool but never spends it: rations are consumed by the sheet's own
 * "consume rations" button, and making daily preparations spend food as a side effect would change
 * what every existing table sees at rest time.
 */
suspend fun tickNightlyStarvation(
    game: Game,
    camping: CampingData,
    party: PF2EParty?,
    actors: List<PF2EActor>,
): List<StarvationCrossing> {
    if (actors.isEmpty()) return emptyList()

    // A recipe only feeds someone if somebody actually cooked it. With no cook assigned the module
    // already downgrades every chosen recipe to rations (see findCookingChoices); mirror that here
    // rather than crediting a meal nobody made.
    val hasCook = camping.campingActivities[cookMealId]?.actorUuid != null
    val chosenByUuid = camping.cooking.actorMeals.asSequence()
        .map { (_, meal) -> meal.actorUuid to meal.chosenMeal }
        .toMap()

    val meals = actors.map { actor ->
        val chosen = chosenByUuid[actor.uuid]
        val kind = when {
            // No entry at all means nobody arranged food for them, which counts as going without.
            chosen == null || chosen == "nothing" -> NightlyMealKind.NOTHING
            chosen == "rationsOrSubsistence" -> NightlyMealKind.RATIONS
            hasCook -> NightlyMealKind.COOKED_MEAL
            else -> NightlyMealKind.RATIONS
        }
        NightlyMealChoice(actorUuid = actor.uuid, kind = kind, rationCost = 1)
    }

    val availableRations = runCatching {
        camping.getTotalCarriedFood(party, getCompendiumFoodItems()).rations
    }.getOrNull() ?: 0
    val feeding = resolveNightlyFeeding(meals, availableRations)

    // Anyone who planned on rations that are gone forages for the night. The roll is the player's
    // to make; a dismissed dialog returns null and simply leaves them hungry.
    val defaults = subsistDefaults(camping)
    val fedBySubsist = mutableSetOf<String>()
    feeding.mustSubsist.forEach { uuid ->
        val character = actors.find { it.uuid == uuid } as? PF2ECharacter ?: return@forEach
        val result = runCatching {
            rollSubsist(
                game = game,
                actor = character,
                skill = defaults.skill,
                dc = defaults.dc,
                subsistPenalty = true,
            )
        }.getOrNull() ?: return@forEach
        postSubsistProvisionsOffer(character, result.provisions)
        if (result.fed) fedBySubsist.add(uuid)
    }

    val crossings = mutableListOf<StarvationCrossing>()
    actors.forEach { actor ->
        val uuid = actor.uuid
        val fed = uuid in feeding.fed || uuid in fedBySubsist
        val previous = camping.daysWithoutFoodFor(uuid)
        val next = tickStarvation(StarvationState(previous), fed).daysWithoutFood
        camping.setDaysWithoutFood(uuid, next)
        val conMod = actorConstitutionModifier(actor)
        if (crossedStarvationThreshold(previous, next, conMod)) {
            crossings.add(
                StarvationCrossing(
                    actorUuid = uuid,
                    actorName = actor.name ?: uuid,
                    daysWithoutFood = next,
                    severity = starvationSeverity(next, conMod),
                )
            )
        }
    }
    return crossings
}
