package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.actor.hasFeat
import at.posselt.pfrpg2e.actor.investedArmor
import at.posselt.pfrpg2e.actor.proficiency
import at.posselt.pfrpg2e.data.actor.Proficiency
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import com.foundryvtt.pf2e.actions.CheckDC
import com.foundryvtt.pf2e.actions.SingleCheckActionUseOptions
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.pf2e
import js.objects.recordOf
import kotlinx.coroutines.await

/**
 * The Subsist action, callable from somewhere other than its macro.
 *
 * Extracted so the nightly meal loop can make the same roll: an actor who chose "Rations or
 * Subsistence" and has nothing left must forage for the night, and that is exactly the Subsist
 * check the macro already implements. Duplicating the roll would have meant duplicating the
 * Forager/Coyote Cloak yield table with it.
 *
 * See card t_40652699.
 */

/** One Subsist attempt: the degree rolled and the provisions it produced. */
data class SubsistResult(
    val degree: DegreeOfSuccess,
    val provisions: Int,
) {
    /** Whether this attempt fed the forager tonight. */
    val fed: Boolean get() = subsistFedActor(provisions)
}

/** The skill and DC a Subsist prompt should start from, given where the party currently is. */
data class SubsistDefaults(
    val skill: String,
    val dc: Int,
)

/**
 * Region-derived defaults: the current zone's DC, and Society rather than Survival in urban
 * terrain. Falls back to DC 15 / Survival when there is no camping region to read.
 */
fun subsistDefaults(camping: CampingData?): SubsistDefaults {
    val currentRegion = camping?.findCurrentRegion()
    val isUrban = currentRegion?.terrain?.let { fromCamelCase<Terrain>(it) } == Terrain.URBAN
    return SubsistDefaults(
        skill = if (isUrban) "society" else "survival",
        dc = currentRegion?.zoneDc ?: 15,
    )
}

/**
 * Rolls PF2e's "subsist" system action for [actor] and prices the outcome in provisions.
 *
 * Returns null when the roll produced no outcome — the player dismissed the check dialog, or the
 * system action is unavailable. The macro previously dereferenced that case with `!!` and threw;
 * a null return turns a cancelled roll into a no-op, which matters far more now that this runs
 * inside daily preparations where an exception would abandon the rest of the night's bookkeeping.
 */
suspend fun rollSubsist(
    game: Game,
    actor: PF2ECharacter,
    skill: String,
    dc: Int,
    subsistPenalty: Boolean,
): SubsistResult? {
    val options = SingleCheckActionUseOptions(
        difficultyClass = CheckDC(value = dc),
        rollOptions = if (subsistPenalty) arrayOf("action:subsist:after-exploration") else emptyArray(),
        statistic = skill,
        actors = arrayOf(actor),
    )
    val outcome = game.pf2e.actions.get("subsist")?.use(options)?.await()?.firstOrNull()?.outcome
        ?: return null
    val degree = fromCamelCase<DegreeOfSuccess>(outcome) ?: return null
    return SubsistResult(
        degree = degree,
        provisions = calculateProvisions(
            survivalProficiency = actor.skills["survival"]?.proficiency ?: Proficiency.UNTRAINED,
            isForager = actor.hasFeat("forager"),
            hasCoyoteCloak = actor.investedArmor("coyote-cloak"),
            hasCoyoteCloakGreat = actor.investedArmor("coyote-cloak-greater"),
            degree = degree,
        ),
    )
}

/**
 * Posts the existing Subsist card carrying the "gain provisions" button.
 *
 * Unchanged in shape and template from the macro, so the shipped compendium macro and its button
 * binding (`.gain-provisions` in CampingChat) keep working exactly as before.
 */
suspend fun postSubsistProvisionsOffer(actor: PF2ECharacter, provisions: Int) {
    if (provisions <= 0) return
    postChatTemplate(
        templateContext = recordOf(
            "provisions" to provisions,
            "actorUuid" to actor.uuid,
            "actorName" to actor.name,
        ),
        templatePath = "chatmessages/subsist.hbs",
        speaker = actor,
    )
}
