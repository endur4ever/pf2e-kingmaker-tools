package at.posselt.pfrpg2e.expedition

import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface ExpeditionActivityOutcome {
    var message: String?
}

@JsPlainObject
external interface ExpeditionSkill {
    val name: String
    val proficiency: String
    val dcType: String // zone, actorLevel, static, none
    val dc: Int?
    val required: Boolean?
}

@JsPlainObject
external interface ExpeditionActivityData {
    var id: String
    var name: String
    var skills: Array<ExpeditionSkill>
    var isSecret: Boolean
    var isLocked: Boolean
    var isHomebrew: Boolean
    var criticalSuccess: ExpeditionActivityOutcome?
    var success: ExpeditionActivityOutcome?
    var failure: ExpeditionActivityOutcome?
    var criticalFailure: ExpeditionActivityOutcome?
    var oncePerSession: Boolean
}

fun ExpeditionActivityData.getOutcome(degreeOfSuccess: DegreeOfSuccess) =
    when (degreeOfSuccess) {
        DegreeOfSuccess.CRITICAL_FAILURE -> criticalFailure
        DegreeOfSuccess.FAILURE -> failure
        DegreeOfSuccess.SUCCESS -> success
        DegreeOfSuccess.CRITICAL_SUCCESS -> criticalSuccess
    }

enum class ExpeditionDcType: ValueEnum, Translatable {
    ZONE,
    ACTOR_LEVEL,
    NONE,
    STATIC;

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "expeditionActivityDcType.$value"
}

@JsModule("./expedition-activities.json")
private external val expeditionActivityData: Array<ExpeditionActivityData>

private fun ExpeditionActivityData.translate() =
    ExpeditionActivityData.copy(
        this,
        name = t(name),
        criticalSuccess = criticalSuccess?.let { ExpeditionActivityOutcome(message = it.message?.let { m -> t(m) }) },
        success = success?.let { ExpeditionActivityOutcome(message = it.message?.let { m -> t(m) }) },
        failure = failure?.let { ExpeditionActivityOutcome(message = it.message?.let { m -> t(m) }) },
        criticalFailure = criticalFailure?.let { ExpeditionActivityOutcome(message = it.message?.let { m -> t(m) }) },
    )

private var translatedExpeditionActivities = emptyArray<ExpeditionActivityData>()

fun translateExpeditionActivities() {
    translatedExpeditionActivities = expeditionActivityData
        .map { it.translate() }
        .toTypedArray()
}

fun getExpeditionActivities(): Array<ExpeditionActivityData> =
    translatedExpeditionActivities

/**
 * Get the localized name for an expedition activity by its ID.
 * Returns the ID if not found (fallback for display).
 */
fun getExpeditionActivityName(activityId: String): String =
    translatedExpeditionActivities.find { it.id == activityId }?.name ?: activityId
