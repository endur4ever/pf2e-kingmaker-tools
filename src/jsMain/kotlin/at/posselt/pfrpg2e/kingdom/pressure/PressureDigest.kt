package at.posselt.pfrpg2e.kingdom.pressure

import at.posselt.pfrpg2e.campaign.CampaignClockManager
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.data.events.KingdomEventTrait
import at.posselt.pfrpg2e.kingdom.RawOngoingKingdomEvent
import at.posselt.pfrpg2e.kingdom.data.RawScheduledPressure
import at.posselt.pfrpg2e.kingdom.dialogs.pickEventSettlement
import at.posselt.pfrpg2e.kingdom.getAllSettlements
import at.posselt.pfrpg2e.kingdom.getEvent
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.logToCalendar
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * The firing digest and its GM offers
 * (`docs/plans/2026-07-09-plan-scheduled-pressure-engine.md` SS7): one whispered card per tick
 * groups every firing by day; each row is an OFFER -- the payload applies on confirm only, never
 * automatically, and escalation is a suggestion in prose, not an applied number.
 *
 * The tick already stamped lastFiredDay/escalationCount (detection happens once); what the
 * buttons manage is [RawScheduledPressure.lastHandledDay] -- the double-apply guard. Confirm and
 * dismiss both stamp it, so a declined firing stops counting as pending, and Confirm-all can be
 * driven from DATA (pending rows), never from card DOM.
 */

@Suppress("unused")
@JsPlainObject
external interface PressureDigestRow {
    val scheduleId: String
    val name: String
    val payloadLabel: String
    /** Beat text preview for postBeat rows; null otherwise. */
    val preview: String?
    /** "escalation 3" suffix; null on a first firing. */
    val escalationLabel: String?
}

@Suppress("unused")
@JsPlainObject
external interface PressureDigestDay {
    val dayLabel: String
    val rows: Array<PressureDigestRow>
}

@Suppress("unused")
@JsPlainObject
external interface PressureDigestContext {
    val title: String
    val actorUuid: String
    val days: Array<PressureDigestDay>
    val confirmLabel: String
    val dismissLabel: String
    val confirmAllLabel: String
    val dismissAllLabel: String
    val showAllButtons: Boolean
}

private const val BEAT_PREVIEW_LENGTH = 80

/**
 * Literal keys mapped in a when -- t("...$kind") would be invisible to check_i18n_keys.py and
 * ship as a raw key with every guard green (plan SS8).
 */
fun payloadLabelFor(kind: PayloadKind?): String = when (kind) {
    PayloadKind.SPAWN_EVENT -> t("kingdom.deadlines.payload.spawnEvent")
    PayloadKind.SPAWN_ENCOUNTER -> t("kingdom.deadlines.payload.spawnEncounter")
    PayloadKind.ADVANCE_CLOCK -> t("kingdom.deadlines.payload.advanceClock")
    PayloadKind.POST_BEAT -> t("kingdom.deadlines.payload.postBeat")
    null -> t("kingdom.deadlines.payload.unknown")
}

fun buildPressureDigestContext(
    firings: List<PressureFiring>,
    rawById: Map<String, RawScheduledPressure>,
    actorUuid: String,
    currentDay: Int,
): PressureDigestContext {
    val days = firings
        .groupBy { it.day }
        .toList()
        .sortedBy { (day, _) -> day }
        .map { (day, dayFirings) ->
            val daysAgo = currentDay - day
            PressureDigestDay(
                dayLabel = if (daysAgo <= 0) {
                    t("kingdom.deadlines.digestToday")
                } else {
                    t("kingdom.deadlines.digestDaysAgo", recordOf("days" to daysAgo.toString()))
                },
                rows = dayFirings.map { firing ->
                    val raw = rawById[firing.schedule.id]
                    val kind = PayloadKind.fromValue(raw?.payloadKind)
                    PressureDigestRow(
                        scheduleId = firing.schedule.id,
                        name = firing.schedule.name,
                        payloadLabel = payloadLabelFor(kind),
                        preview = raw?.payloadBeatText
                            ?.takeIf { kind == PayloadKind.POST_BEAT }
                            ?.take(BEAT_PREVIEW_LENGTH),
                        escalationLabel = firing.escalation
                            .takeIf { it > 1 }
                            ?.let { t("kingdom.deadlines.escalation", recordOf("count" to it.toString())) },
                    )
                }.toTypedArray(),
            )
        }
        .toTypedArray()
    return PressureDigestContext(
        title = t("kingdom.deadlines.digestTitle"),
        actorUuid = actorUuid,
        days = days,
        confirmLabel = t("kingdom.deadlines.confirm"),
        dismissLabel = t("kingdom.deadlines.dismiss"),
        confirmAllLabel = t("kingdom.deadlines.confirmAll"),
        dismissAllLabel = t("kingdom.deadlines.dismissAll"),
        showAllButtons = firings.size > 1,
    )
}

/** Pending = fired more recently than the GM last acted on it. Drives the -all buttons from data. */
fun pendingPressureRows(pressures: Array<RawScheduledPressure>?): List<RawScheduledPressure> =
    (pressures ?: emptyArray()).filter { raw ->
        val fired = raw.lastFiredDay ?: return@filter false
        fired > (raw.lastHandledDay ?: Int.MIN_VALUE)
    }

/**
 * Applies one pending firing's payload through the system that OWNS it, stamps lastHandledDay,
 * and writes the calendar note (confirm only -- a dismissal leaves no trace, plan SS7).
 * Returns false when nothing was pending (double click, stale card).
 */
suspend fun confirmPressureFiring(game: Game, actor: KingdomActor, scheduleId: String): Boolean {
    val opening = actor.getKingdom() ?: return false
    val openingRaw = opening.scheduledPressures?.find { it.id == scheduleId } ?: return false
    if ((openingRaw.lastFiredDay ?: return false) <= (openingRaw.lastHandledDay ?: Int.MIN_VALUE)) {
        ui.notifications.warn(t("kingdom.deadlines.alreadyHandled", recordOf("name" to openingRaw.name)))
        return false
    }
    val kind = PayloadKind.fromValue(openingRaw.payloadKind)

    // The settlement picker is the only await between reading the kingdom and writing it back, and
    // everything below mutates the object read here. Resolving it FIRST, then re-reading, keeps the
    // whole mutation in one uninterrupted stretch -- otherwise whatever changed while the GM had
    // the dialog open was overwritten by this pre-dialog snapshot.
    val settlementPick = if (kind == PayloadKind.SPAWN_EVENT) {
        val event = openingRaw.payloadEventId?.let { opening.getEvent(it) }
        if (event != null && KingdomEventTrait.SETTLEMENT.value in event.traits) {
            pickEventSettlement(opening.getAllSettlements(game).allSettlements)
        } else {
            null
        }
    } else {
        null
    }

    val kingdom = actor.getKingdom() ?: return false
    val raw = kingdom.scheduledPressures?.find { it.id == scheduleId } ?: return false
    val firedDay = raw.lastFiredDay ?: return false
    // re-checked against the fresh read: another GM may have answered this firing while the picker
    // stood open, and applying it twice is the thing this stamp exists to prevent
    if (firedDay <= (raw.lastHandledDay ?: Int.MIN_VALUE)) {
        ui.notifications.warn(t("kingdom.deadlines.alreadyHandled", recordOf("name" to raw.name)))
        return false
    }
    when (kind) {
        PayloadKind.POST_BEAT -> {
            // public read-aloud beat; postChatMessage escapes -- authored text is prose, not HTML
            val text = raw.payloadBeatText ?: ""
            postChatMessage(t("kingdom.deadlines.beat", recordOf("name" to raw.name, "text" to text)))
        }

        PayloadKind.ADVANCE_CLOCK -> {
            val clockId = raw.payloadClockId
            val clock = kingdom.campaignClocks.find { it.id == clockId }
            if (clock == null) {
                ui.notifications.warn(t("kingdom.deadlines.clockMissing", recordOf("name" to raw.name)))
            } else {
                // the owning system: tickAll on just this clock reuses expiry/pause semantics
                val result = CampaignClockManager.tickAll(arrayOf(clock))
                kingdom.campaignClocks = kingdom.campaignClocks.map { existing ->
                    result.updatedClocks.find { it.id == existing.id } ?: existing
                }.toTypedArray()
                kingdom.unrest += result.totalUnrestChange
                val updated = result.updatedClocks.find { it.id == clock.id }
                ui.notifications.info(
                    t(
                        "kingdom.deadlines.clockAdvanced",
                        recordOf(
                            "label" to clock.label,
                            "turns" to (updated?.turnsRemaining ?: 0).toString(),
                        ),
                    )
                )
            }
        }

        PayloadKind.SPAWN_EVENT -> {
            val eventId = raw.payloadEventId
            val event = eventId?.let { kingdom.getEvent(it) }
            if (event == null) {
                ui.notifications.warn(t("kingdom.deadlines.eventMissing", recordOf("name" to raw.name)))
            } else {
                val ongoing = if (settlementPick != null) {
                    RawOngoingKingdomEvent(
                        stage = 0,
                        id = eventId,
                        settlementSceneId = settlementPick.settlementId,
                        secretLocation = settlementPick.secretLocation,
                    )
                } else {
                    RawOngoingKingdomEvent(stage = 0, id = eventId)
                }
                kingdom.ongoingEvents = kingdom.ongoingEvents + ongoing
                if (raw.escalationCount > 1) {
                    // escalation is a SUGGESTED step, surfaced in prose, never applied (plan SS6)
                    ui.notifications.info(
                        t("kingdom.deadlines.escalationHint", recordOf("count" to raw.escalationCount.toString()))
                    )
                }
            }
        }

        PayloadKind.SPAWN_ENCOUNTER -> {
            // no kingdom-side encounter queue exists to write into; the confirm is a GM pointer,
            // not an invented mechanic -- the encounter itself runs through its own surface
            postChatMessage(
                t(
                    "kingdom.deadlines.encounterPrompt",
                    recordOf(
                        "name" to raw.name,
                        "ref" to (raw.payloadEncounterId ?: "?"),
                        "escalation" to raw.escalationCount.toString(),
                    ),
                ),
                whisper = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray(),
            )
        }

        null -> ui.notifications.warn(t("kingdom.deadlines.payload.unknown"))
    }
    raw.lastHandledDay = firedDay
    actor.setKingdom(kingdom)
    if (kind != null) {
        logToCalendar(
            title = raw.name,
            content = t(
                "kingdom.deadlines.confirmedNote",
                recordOf("name" to raw.name, "payload" to payloadLabelFor(kind)),
            ),
        )
    }
    return true
}

/** Stamps the pending firing handled WITHOUT applying anything or writing a calendar note. */
suspend fun dismissPressureFiring(actor: KingdomActor, scheduleId: String): Boolean {
    val kingdom = actor.getKingdom() ?: return false
    val raw = kingdom.scheduledPressures?.find { it.id == scheduleId } ?: return false
    val firedDay = raw.lastFiredDay ?: return false
    if (firedDay <= (raw.lastHandledDay ?: Int.MIN_VALUE)) return false
    raw.lastHandledDay = firedDay
    actor.setKingdom(kingdom)
    return true
}
