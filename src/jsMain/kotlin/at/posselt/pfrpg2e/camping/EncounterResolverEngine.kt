package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * Results of tonight's camp-defense activities, typed as [DegreeOfSuccess] so callers can pass
 * `parseResult()` output directly — matching on serialized degree STRINGS is what silently
 * disabled every critical result in the first cut of this feature.
 */
@JsExport
@JsName("CampDefenseState")
data class CampDefenseState(
    /** Set Alarms result; null when not attempted. */
    val alarmsDegree: DegreeOfSuccess? = null,
    /** Camouflage Campsite result; null when not attempted. */
    val camouflageDegree: DegreeOfSuccess? = null,
    /** Set Traps result; null when not attempted. */
    val trapsDegree: DegreeOfSuccess? = null,
    /** Undead Guardians: whether the activity succeeded (CS/S). Null when not attempted. */
    val undeadGuardiansActive: Boolean? = null,
)

/**
 * The Perception bonus Set Alarms grants the watcher against the ambusher's Stealth:
 * +4 on a critical success, +2 on a success, -2 on a critical failure (jury-rigged tripwires
 * ring at the wrong moments). The REST FLOW applies this to the watch roll's DC BEFORE rolling,
 * so it changes the actual outcome degree; the resolver only reports it as a contribution line.
 */
fun alarmsPerceptionBonus(degree: DegreeOfSuccess?): Int = when (degree) {
    DegreeOfSuccess.CRITICAL_SUCCESS -> 4
    DegreeOfSuccess.SUCCESS -> 2
    DegreeOfSuccess.CRITICAL_FAILURE -> -2
    else -> 0
}

@JsExport
data class EncounterResolutionResult(
    val attackerStealthDc: Int,
    val watcherPerceptionRoll: Int,
    val distanceToEnemy: Float,
    val appliedConditions: Array<String>,
    val ambusherState: String,
    val gmNotes: String,
    /** Defense contributions for display in chat card. */
    val defenseContributions: Array<String>
)

@JsExport
@JsName("EncounterResolverEngine")
object EncounterResolverEngine {
    /**
     * Resolves a night ambush encounter.
     *
     * Camp defense effects:
     * - Set Alarms: [alarmsPerceptionBonus] is folded into the watch roll's DC by the rest flow
     *   BEFORE the roll, so [degree] already reflects it; here it only adds a contribution line.
     * - Camouflage Campsite (CS): the ambusher is spotted a full band farther out (60 -> 120);
     *   (S): +15 ft; (CF): the ambusher slips a band closer (60 -> 15). Larger distance always
     *   favors the party — a defense the party invested in must never pull the enemy closer.
     * - Set Traps (CS/S): adds a one-time trap damage/disruption line to the resolution output.
     * - Undead Guardians (CS/S): the guardians stand an extra watch — advisory line for the GM.
     */
    fun resolve(
        watcherRoll: Int,
        stealthDc: Int,
        degree: DegreeOfSuccess,
        defenseState: CampDefenseState = CampDefenseState()
    ): EncounterResolutionResult {
        // Determine base resolution from degree (the roll shown is the REAL roll — the Set Alarms
        // bonus already influenced the degree via the DC, inflating the display would double-count)
        val baseResult = when (degree) {
            DegreeOfSuccess.CRITICAL_SUCCESS -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = watcherRoll,
                distanceToEnemy = 120.0f,
                appliedConditions = emptyArray(),
                ambusherState = "Revealed",
                gmNotes = "The watcher detects the enemy early. The enemy is startled, starting at a long distance (120 ft) with no surprise penalty to the party.",
                defenseContributions = emptyArray()
            )
            DegreeOfSuccess.SUCCESS -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = watcherRoll,
                distanceToEnemy = 60.0f,
                appliedConditions = arrayOf("prone"),
                ambusherState = "Revealed",
                gmNotes = "The watcher detects the enemy. Sleeping party members wake up but start prone (60 ft distance).",
                defenseContributions = emptyArray()
            )
            DegreeOfSuccess.FAILURE -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = watcherRoll,
                distanceToEnemy = 60.0f,
                appliedConditions = arrayOf("unconscious", "prone"),
                ambusherState = "Hidden",
                gmNotes = "The enemy successfully ambushes the party (60 ft distance). Sleeping party members remain asleep (unconscious and prone).",
                defenseContributions = emptyArray()
            )
            DegreeOfSuccess.CRITICAL_FAILURE -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = watcherRoll,
                distanceToEnemy = 15.0f,
                appliedConditions = arrayOf("unconscious", "prone"),
                ambusherState = "Hidden",
                gmNotes = "The party is severely ambushed at close distance (15 ft). Sleeping party members remain asleep (unconscious and prone) and do not gain Reactions before their turn starts.",
                defenseContributions = emptyArray()
            )
        }

        // Camouflage shifts where the ambusher is finally noticed. Positive = farther from camp
        // (party-favorable): a well-hidden camp forces the ambusher to search in the open.
        val camouflageDistanceAdjustment = when (defenseState.camouflageDegree) {
            DegreeOfSuccess.CRITICAL_SUCCESS -> 60.0f   // spotted a full band farther out
            DegreeOfSuccess.SUCCESS -> 15.0f            // spotted half a band farther out
            DegreeOfSuccess.CRITICAL_FAILURE -> -60.0f  // the botched job guides them in closer
            else -> 0.0f
        }

        val contributions = mutableListOf<String>()

        defenseState.alarmsDegree?.let { alarms ->
            val bonus = alarmsPerceptionBonus(alarms)
            contributions.add(
                when {
                    bonus > 0 -> "Set Alarms: +$bonus Perception vs the ambusher's Stealth"
                    bonus < 0 -> "Set Alarms (Critical Failure): $bonus Perception vs the ambusher's Stealth"
                    else -> "Set Alarms (Failure): no benefit"
                }
            )
        }

        defenseState.camouflageDegree?.let { camo ->
            contributions.add(
                when (camo) {
                    DegreeOfSuccess.CRITICAL_SUCCESS -> "Camouflage Campsite (Critical Success): ambusher spotted a full band farther out"
                    DegreeOfSuccess.SUCCESS -> "Camouflage Campsite (Success): ambusher spotted farther out (+15 ft)"
                    DegreeOfSuccess.FAILURE -> "Camouflage Campsite (Failure): no benefit"
                    DegreeOfSuccess.CRITICAL_FAILURE -> "Camouflage Campsite (Critical Failure): the ambusher slips a band closer"
                }
            )
        }

        when (defenseState.trapsDegree) {
            DegreeOfSuccess.CRITICAL_SUCCESS ->
                contributions.add("Set Traps (Critical Success): trap triggers for 4d6 damage and disrupts the ambush")
            DegreeOfSuccess.SUCCESS ->
                contributions.add("Set Traps (Success): trap triggers for 2d6 damage and disrupts the ambush")
            else -> {}
        }

        if (defenseState.undeadGuardiansActive == true) {
            contributions.add("Undead Guardians: the guardians stand an extra watch over the camp")
        }

        return baseResult.copy(
            distanceToEnemy = (baseResult.distanceToEnemy + camouflageDistanceAdjustment)
                .coerceAtLeast(15.0f)
                .coerceAtMost(120.0f),
            defenseContributions = contributions.toTypedArray()
        )
    }
}
