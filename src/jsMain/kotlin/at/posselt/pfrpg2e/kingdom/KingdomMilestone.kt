package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.MilestoneChoice
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawMilestone {
    var id: String
    var name: String
    var xp: Int
    var completed: Boolean
    var enabledOnFirstRun: Boolean
    var isCultMilestone: Boolean

    /**
     * Wires this milestone to a detector in `deedDetectors` (deeds-chronicle plan 3.1).
     * Absent = GM-awarded only, which is every adventure-path beat in the shipped catalog.
     */
    var detectionId: String?
}

@JsModule("./milestones.json")
private external val kingdomMilestones: Array<RawMilestone>

private fun RawMilestone.translate() =
    RawMilestone.copy(
        this,
        name = t(name),
    )

private var translatedMilestones = emptyArray<RawMilestone>()

fun translateMilestones() {
    translatedMilestones = kingdomMilestones
        .map { it.translate() }
        .toTypedArray()
}

val initialMilestoneChoices = kingdomMilestones
    .map {
        MilestoneChoice(
            id = it.id,
            completed = false,
            enabled = it.enabledOnFirstRun,
        )
    }
    .toTypedArray()

fun KingdomData.getMilestones(): Array<RawMilestone> {
    val overrides = homebrewMilestones.map { it.id }.toSet()
    return homebrewMilestones + translatedMilestones.filter { it.id !in overrides }
}

/** The raw bundled catalog, for guards that must read the DATA rather than the translated view. */
fun kingdomMilestonesForTest(): Array<RawMilestone> = kingdomMilestones
