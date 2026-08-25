package at.posselt.pfrpg2e.kingdom.data

import at.posselt.pfrpg2e.kingdom.downtime.DowntimeKind
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeProject
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeStatus
import kotlinx.js.JsPlainObject

/**
 * Persisted shape of one PC downtime project
 * (`docs/plans/2026-07-09-plan-downtime-projects.md` §4).
 *
 * [kind] and [status] are STRINGS, not enums: this is a `@JsPlainObject` serialized into a Foundry
 * flag, the same reason `RawCompanionExpedition.status` is a String. Enums live in commonMain and
 * convert at this boundary; a value this build does not recognise drops the row rather than
 * throwing, so one bad project cannot take down the daily tick.
 */
@JsPlainObject
external interface RawPcDowntimeProject {
    var id: String
    var pcActorUuid: String
    /** craft | retrain | earnIncome | ritual */
    var kind: String
    /** Item uuid, feat name, or ritual name, by kind. */
    var targetRef: String
    /** Display label captured at creation, so the row still reads if the target is deleted. */
    var title: String
    var settlementId: String?
    var daysTotal: Int
    var daysRemaining: Int
    /** Gold per day, if the kind charges one. */
    var dailyCostGp: Double?
    /** inProgress | paused | completed */
    var status: String
    /** Why it paused, for the row's tooltip; null unless paused. */
    var pauseReason: String?
}

/** Null when [RawPcDowntimeProject.kind] or [RawPcDowntimeProject.status] is unrecognised. */
fun RawPcDowntimeProject.toModel(): DowntimeProject? {
    val kind = DowntimeKind.fromValue(kind) ?: return null
    val status = DowntimeStatus.fromValue(status) ?: return null
    return DowntimeProject(
        id = id,
        pcActorUuid = pcActorUuid,
        kind = kind,
        title = title,
        settlementId = settlementId,
        daysTotal = daysTotal,
        daysRemaining = daysRemaining,
        status = status,
        pauseReason = pauseReason,
    )
}

fun DowntimeProject.toRaw(targetRef: String, dailyCostGp: Double?): RawPcDowntimeProject =
    RawPcDowntimeProject(
        id = id,
        pcActorUuid = pcActorUuid,
        kind = kind.value,
        targetRef = targetRef,
        title = title,
        settlementId = settlementId,
        daysTotal = daysTotal,
        daysRemaining = daysRemaining,
        dailyCostGp = dailyCostGp,
        status = status.value,
        pauseReason = pauseReason,
    )
