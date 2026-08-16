package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.kingdom.dialogs.postComplexDegreeOfSuccess
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe

@JsPlainObject
external interface UpgradeMetaContext {
    val rollMode: String
    val activityId: String?
    val eventId: String?
    val eventStageIndex: Int
    val degree: String
    val additionalChatMessages: String?
    val notes: String?
    val actorUuid: String
    val eventIndex: Int

    /**
     * The check's DC, total and die value, carried so a banked-aid spend can re-evaluate the degree
     * from the same numbers the original roll used. Nullable because event/legacy cards predate them.
     */
    val dc: Int?
    val total: Int?
    val dieValue: Int?
}

fun parseUpgradeMeta(elem: HTMLElement) =
    UpgradeMetaContext(
        rollMode = elem.dataset["rollMode"] ?: "",
        activityId = elem.dataset["activityId"],
        degree = elem.dataset["degree"] ?: "",
        additionalChatMessages = elem.dataset["additionalChatMessages"],
        notes = elem.dataset["notes"],
        actorUuid = elem.dataset["kingdomActorUuid"] ?: "",
        eventId = elem.dataset["eventId"],
        eventStageIndex = elem.dataset["eventStageIndex"]?.toInt() ?: 0,
        eventIndex = elem.dataset["eventIndex"]?.toInt() ?: 0,
        dc = elem.dataset["dc"]?.toIntOrNull(),
        total = elem.dataset["total"]?.toIntOrNull(),
        dieValue = elem.dataset["dieValue"]?.toIntOrNull(),
    )

enum class ChangeDegree {
    UPGRADE,
    DOWNGRADE;
}

suspend fun changeDegree(rollMeta: HTMLElement, mode: ChangeDegree) {
    val meta = parseUpgradeMeta(rollMeta)
    val degree = DegreeOfSuccess.fromString(meta.degree)
    val changed = if (mode == ChangeDegree.UPGRADE) {
        when (degree) {
            DegreeOfSuccess.CRITICAL_FAILURE -> DegreeOfSuccess.FAILURE
            DegreeOfSuccess.FAILURE -> DegreeOfSuccess.SUCCESS
            DegreeOfSuccess.SUCCESS -> DegreeOfSuccess.CRITICAL_SUCCESS
            else -> null
        }
    } else {
        when (degree) {
            DegreeOfSuccess.FAILURE -> DegreeOfSuccess.CRITICAL_FAILURE
            DegreeOfSuccess.SUCCESS -> DegreeOfSuccess.FAILURE
            DegreeOfSuccess.CRITICAL_SUCCESS -> DegreeOfSuccess.SUCCESS
            else -> null
        }
    }
    if (changed == null) {
        console.error("Can not upgrade degree $degree")
    } else {
        postComplexDegreeOfSuccess(meta, changed)
        // The lockout was written from the ORIGINAL degree by rollCheck. A GM adjustment supersedes
        // that result, so re-derive it here -- otherwise an upgraded critical failure stayed locked
        // for the full original timeout, and a degree downgraded INTO a failure was never locked at
        // all. recordActivityUse now assigns the lock from the new degree, so both directions work.
        val activityId = meta.activityId
        if (activityId != null && activityTracksUsage(activityId)) {
            fromUuidTypeSafe<KingdomActor>(meta.actorUuid)?.let { actor ->
                actor.getKingdom()?.let { kingdom ->
                    kingdom.activityUsage = recordActivityUse(
                        kingdom.activityUsages(),
                        activityId,
                        changed,
                        kingdom.currentTurn ?: 0,
                    ).toRawActivityBlocks()
                    actor.setKingdom(kingdom)
                }
            }
        }
    }
}