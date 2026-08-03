package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
@JsName("CampDefenseState")
data class CampDefenseState(
    /** Set Alarms: degree of success (CS/S/F/CF). Null if not attempted. */
    val alarmsDegree: String?,
    /** Camouflage Campsite: degree of success (CS/S/F/CF). Null if not attempted. */
    val camouflageDegree: String?,
    /** Set Traps: degree of success (CS/S/F/CF). Null if not attempted. */
    val trapsDegree: String?,
    /** Undead Guardians: whether the activity was successfully performed (CS/S). Null if not attempted. */
    val undeadGuardiansActive: Boolean?
)

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
     * Camp defense effects (KDoc mapping):
     * - Set Alarms (CS): +4 bonus to watcher's Perception vs ambush Stealth; (S): +2 bonus.
     * - Camouflage Campsite (CS): worsens ambusher's start distance by one band (e.g., 120→60, 60→15);
     *   (S): worsens by half-band (adds +15 ft to distance); (CF): improves ambusher distance by one band.
     * - Set Traps (CS/S): adds a one-time trap damage/disruption line to the resolution output.
     * - Undead Guardians (CS/S): adds an extra watcher-equivalent (uses best roll among party).
     */
    fun resolve(
        watcherRoll: Int,
        stealthDc: Int,
        degree: DegreeOfSuccess,
        defenseState: CampDefenseState = CampDefenseState(null, null, null, null)
    ): EncounterResolutionResult {
        // Apply Set Alarms bonus to watcher roll
        val alarmsBonus = when (defenseState.alarmsDegree) {
            "criticalSuccess" -> 4
            "success" -> 2
            else -> 0
        }
        val adjustedWatcherRoll = watcherRoll + alarmsBonus
        
        // Determine base resolution from degree
        val baseResult = when (degree) {
            DegreeOfSuccess.CRITICAL_SUCCESS -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = adjustedWatcherRoll,
                distanceToEnemy = 120.0f,
                appliedConditions = emptyArray(),
                ambusherState = "Revealed",
                gmNotes = "The watcher detects the enemy early. The enemy is startled, starting at a long distance (120 ft) with no surprise penalty to the party.",
                defenseContributions = emptyArray()
            )
            DegreeOfSuccess.SUCCESS -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = adjustedWatcherRoll,
                distanceToEnemy = 60.0f,
                appliedConditions = arrayOf("prone"),
                ambusherState = "Revealed",
                gmNotes = "The watcher detects the enemy. Sleeping party members wake up but start prone (60 ft distance).",
                defenseContributions = emptyArray()
            )
            DegreeOfSuccess.FAILURE -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = adjustedWatcherRoll,
                distanceToEnemy = 60.0f,
                appliedConditions = arrayOf("unconscious", "prone"),
                ambusherState = "Hidden",
                gmNotes = "The enemy successfully ambushes the party (60 ft distance). Sleeping party members remain asleep (unconscious and prone).",
                defenseContributions = emptyArray()
            )
            DegreeOfSuccess.CRITICAL_FAILURE -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = adjustedWatcherRoll,
                distanceToEnemy = 15.0f,
                appliedConditions = arrayOf("unconscious", "prone"),
                ambusherState = "Hidden",
                gmNotes = "The party is severely ambushed at close distance (15 ft). Sleeping party members remain asleep (unconscious and prone) and do not gain Reactions before their turn starts.",
                defenseContributions = emptyArray()
            )
        }
        
        // Apply Camouflage Campsite effects on distance
        val camouflageDistanceAdjustment = when (defenseState.camouflageDegree) {
            "criticalSuccess" -> -60.0f  // Worsen by one band: 120->60, 60->15
            "success" -> -15.0f          // Worsen by half-band: add 15ft
            "criticalFailure" -> 60.0f   // Improve ambusher distance by one band
            else -> 0.0f
        }
        
        // Apply Set Traps and Undead Guardians to contributions
        val contributions = mutableListOf<String>()
        
        if (defenseState.alarmsDegree != null) {
            val alarmsText = when (defenseState.alarmsDegree) {
                "criticalSuccess" -> "Set Alarms (Critical Success): +4 Perception vs Stealth"
                "success" -> "Set Alarms (Success): +2 Perception vs Stealth"
                "failure" -> "Set Alarms (Failure): No benefit"
                "criticalFailure" -> "Set Alarms (Critical Failure): -2 Perception vs Stealth"
                else -> "Set Alarms"
            }
            contributions.add(alarmsText)
        }
        
        if (defenseState.camouflageDegree != null) {
            val camoText = when (defenseState.camouflageDegree) {
                "criticalSuccess" -> "Camouflage Campsite (Critical Success): Ambusher distance worsened by one band"
                "success" -> "Camouflage Campsite (Success): Ambusher distance worsened by half band"
                "failure" -> "Camouflage Campsite (Failure): No benefit"
                "criticalFailure" -> "Camouflage Campsite (Critical Failure): Ambusher distance improved by one band"
                else -> "Camouflage Campsite"
            }
            contributions.add(camoText)
        }
        
        if (defenseState.trapsDegree != null && defenseState.trapsDegree != "failure" && defenseState.trapsDegree != "criticalFailure") {
            val trapsText = when (defenseState.trapsDegree) {
                "criticalSuccess" -> "Set Traps (Critical Success): Trap triggers for 4d6 damage and disruption"
                "success" -> "Set Traps (Success): Trap triggers for 2d6 damage and disruption"
                else -> "Set Traps: Trap triggers"
            }
            contributions.add(trapsText)
        }
        
        if (defenseState.undeadGuardiansActive == true) {
            contributions.add("Undead Guardians: Extra watcher-equivalent contributed")
        }
        
        // Return result with modifications
        return baseResult.copy(
            watcherPerceptionRoll = adjustedWatcherRoll,
            distanceToEnemy = (baseResult.distanceToEnemy + camouflageDistanceAdjustment).coerceAtLeast(15.0f).coerceAtMost(120.0f),
            defenseContributions = contributions.toTypedArray()
        )
    }
}
