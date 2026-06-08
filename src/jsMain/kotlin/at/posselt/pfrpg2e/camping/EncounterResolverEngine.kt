package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.js.JsExport
import kotlin.js.JsName

@JsExport
data class EncounterResolutionResult(
    val attackerStealthDc: Int,
    val watcherPerceptionRoll: Int,
    val distanceToEnemy: Float,
    val appliedConditions: Array<String>,
    val ambusherState: String,
    val gmNotes: String
)

@JsExport
@JsName("EncounterResolverEngine")
object EncounterResolverEngine {
    fun resolve(
        watcherRoll: Int,
        stealthDc: Int,
        degree: DegreeOfSuccess
    ): EncounterResolutionResult {
        return when (degree) {
            DegreeOfSuccess.CRITICAL_SUCCESS -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = watcherRoll,
                distanceToEnemy = 120.0f,
                appliedConditions = emptyArray(),
                ambusherState = "Revealed",
                gmNotes = "The watcher detects the enemy early. The enemy is startled, starting at a long distance (120 ft) with no surprise penalty to the party."
            )
            DegreeOfSuccess.SUCCESS -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = watcherRoll,
                distanceToEnemy = 60.0f,
                appliedConditions = arrayOf("prone"),
                ambusherState = "Revealed",
                gmNotes = "The watcher detects the enemy. Sleeping party members wake up but start prone (60 ft distance)."
            )
            DegreeOfSuccess.FAILURE -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = watcherRoll,
                distanceToEnemy = 60.0f,
                appliedConditions = arrayOf("unconscious", "prone"),
                ambusherState = "Hidden",
                gmNotes = "The enemy successfully ambushes the party (60 ft distance). Sleeping party members remain asleep (unconscious and prone)."
            )
            DegreeOfSuccess.CRITICAL_FAILURE -> EncounterResolutionResult(
                attackerStealthDc = stealthDc,
                watcherPerceptionRoll = watcherRoll,
                distanceToEnemy = 15.0f,
                appliedConditions = arrayOf("unconscious", "prone"),
                ambusherState = "Hidden",
                gmNotes = "The party is severely ambushed at close distance (15 ft). Sleeping party members remain asleep (unconscious and prone) and do not gain Reactions before their turn starts."
            )
        }
    }
}
