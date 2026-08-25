package at.posselt.pfrpg2e.kingdom.digest

import at.posselt.pfrpg2e.kingdom.CaravanEvent
import at.posselt.pfrpg2e.kingdom.CaravanEventKind
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.utils.getAppFlag
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.setAppFlag
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * jsMain adapters for the "Meanwhile in the Stolen Lands" interlude
 * (`docs/plans/2026-07-09-plan-meanwhile-digest.md` SS3.3, SS3.6): map each End Turn feed's own
 * event types into the pure [DigestEvent] mirror and post ONE public read-aloud card.
 *
 * Feed selection follows the playerNotes precedent at this exact seam (TurnWizardApplication
 * builds the player gazette as "everything except campaign clocks"): the interlude is PUBLIC, so
 * it carries only feeds with an established player-safe surface -- caravans and shipments (cargo
 * is public), the expedition chronicle (already in playerNotes), and war-threat escalations
 * gated on the threat's own visibleToPlayers flag. Campaign clocks and faction standings have NO
 * visibility flag and are GM-facing surfaces, so they are deliberately absent here; adding them
 * needs a visibility field first, not an adapter change.
 */

const val DIGEST_KEY_PREFIX = "kingdom.meanwhile"

/** Relevance tiers (SS3.1): the kingdom's own assets outrank background events. Public so the
 * tests can pin the tier each adapter assigns -- the cross-feed ranking depends on them. */
const val RELEVANCE_CARAVAN_TIER = 1.0
const val RELEVANCE_EXPEDITION_TIER = 0.9
const val RELEVANCE_THREAT_TIER = 0.8

fun caravanDigestEvents(
    events: List<CaravanEvent>,
    feedKind: String,
    turn: Int,
): List<DigestEvent> =
    events.mapIndexed { index, event ->
        val destination = event.destLabel ?: event.partnerName ?: event.summary
        val commodity = event.cargoCommodity ?: event.deliveredCommodity ?: t("$DIGEST_KEY_PREFIX.genericCargo")
        val (labelKey, args) = when (event.kind) {
            CaravanEventKind.RAIDED -> "$DIGEST_KEY_PREFIX.$feedKind.raided" to mapOf(
                "destination" to destination,
                "lost" to event.cargoLost.toString(),
                "commodity" to commodity,
            )
            CaravanEventKind.DELIVERED ->
                if (event.bonusResourceDice > 0) {
                    "$DIGEST_KEY_PREFIX.$feedKind.deliveredRd" to mapOf(
                        "destination" to destination,
                        "rd" to event.bonusResourceDice.toString(),
                    )
                } else {
                    "$DIGEST_KEY_PREFIX.$feedKind.deliveredAmount" to mapOf(
                        "destination" to destination,
                        "amount" to event.deliveredAmount.toString(),
                        "commodity" to commodity,
                    )
                }
            CaravanEventKind.LOST -> "$DIGEST_KEY_PREFIX.$feedKind.lost" to mapOf(
                "destination" to destination,
            )
        }
        DigestEvent(
            id = "$feedKind-$turn-$index",
            kind = feedKind,
            // per-source dedup keys on (kind, sourceName): one route tells at most one beat
            sourceName = destination,
            magnitudeNorm = caravanMagnitude(
                cargoLost = event.cargoLost,
                deliveredAmount = event.deliveredAmount,
                cargoAmount = event.cargoAmount,
                raided = event.kind == CaravanEventKind.RAIDED,
            ),
            relevance = RELEVANCE_CARAVAN_TIER,
            labelKey = labelKey,
            labelArgs = args,
        )
    }

/**
 * Dramatic outcomes deserve the beat: both critical degrees score full magnitude -- a disaster is
 * as tellable as a triumph -- with the plain degrees behind them.
 */
private fun outcomeMagnitude(outcomeDegree: String): Double = when (outcomeDegree) {
    "criticalSuccess", "criticalFailure" -> 1.0
    "success" -> 0.7
    "failure" -> 0.6
    else -> 0.5
}

/**
 * The chronicle is player-visible by design (the expeditions sheet and playerTurnNotes both
 * show it), so no visibility filter applies here -- but note the digest PUSHES these lines
 * into public chat, where the other chronicle surfaces are pull. A GM running a secret
 * expedition should keep it out of the reward flow until the reveal; once a reward is
 * applied the chronicle is public.
 */
fun expeditionDigestEvents(
    chronicle: Array<RawExpeditionChronicleEntry>?,
    turn: Int,
): List<DigestEvent> =
    (chronicle ?: emptyArray())
        .filter { it.turn == turn }
        .mapIndexed { index, entry ->
            val degreeKey = when (entry.outcomeDegree) {
                "criticalSuccess", "success", "failure", "criticalFailure" -> entry.outcomeDegree
                else -> "success"
            }
            DigestEvent(
                id = "expedition-$turn-$index",
                kind = "expedition",
                sourceName = entry.title,
                magnitudeNorm = outcomeMagnitude(entry.outcomeDegree),
                relevance = RELEVANCE_EXPEDITION_TIER,
                labelKey = "$DIGEST_KEY_PREFIX.expedition.$degreeKey",
                labelArgs = mapOf(
                    "companions" to entry.companionNames,
                    "title" to entry.title,
                ),
            )
        }

/**
 * A threat contributes a beat only when this tick actually raised its escalation level AND the
 * threat is player-visible. Null visibility counts as VISIBLE -- that is the field's own
 * contract (RawWarThreat: "null/true = visible", Migration46 backfills null to true) and how
 * the Army Pressure board renders it, so the digest cannot under-report a threat players
 * already see. Magnitude is how far up the escalation ladder it now stands.
 */
fun threatEscalationEvents(
    preTickThreats: List<RawWarThreat>,
    postTickThreats: List<RawWarThreat>,
    turn: Int,
): List<DigestEvent> {
    val preLevels = preTickThreats.associate { it.id to it.escalationLevel }
    return postTickThreats
        .filter { it.visibleToPlayers != false }
        .filter { threat ->
            val pre = preLevels[threat.id]
            pre != null && threat.escalationLevel > pre
        }
        .map { threat ->
            DigestEvent(
                id = "threat-${threat.id}-esc${threat.escalationLevel}",
                kind = "warThreat",
                sourceName = threat.name,
                magnitudeNorm = threat.escalationLevel.toDouble() / maxOf(threat.maxEscalation, 1),
                relevance = RELEVANCE_THREAT_TIER,
                labelKey = "$DIGEST_KEY_PREFIX.warThreat.escalated",
                labelArgs = mapOf(
                    "name" to threat.name,
                    "level" to threat.escalationLevel.toString(),
                    "max" to threat.maxEscalation.toString(),
                ),
            )
        }
        .toList()
}

/**
 * Last digest posted, for cross-turn dedup. With today's feeds every id is turn-scoped, so this
 * is defensive rather than load-bearing -- but the pure core's contract is id-keyed dedup, and a
 * future feed with persistent ids (a slow-burning threat, a standing modifier) gets it for free.
 */
@JsPlainObject
external interface RawDigestRecord {
    var turn: Int
    /** Nullable at the boundary: the flag has no migration path, so a missing array must
     * read as "no baseline", never throw (the getAppFlag cast is unchecked). */
    var beatIds: Array<String>?
}

fun KingdomActor.lastDigestRecord(): RawDigestRecord? =
    getAppFlag<KingdomActor, RawDigestRecord?>("lastDigestBeats")

@Suppress("unused")
@JsPlainObject
external interface DigestCardContext {
    val title: String
    val turnLabel: String
    val beats: Array<String>
}

/**
 * Builds and posts the interlude for one End Turn. PUBLIC chat card -- the whole point is one
 * read-aloud beat instead of a fragmented stream -- so every event fed in here must already be
 * player-safe (see the file KDoc). Dedup only consults the immediately previous turn's record
 * (window 1, the SS2.1 default); an older record means missed turns and a stale baseline.
 * No beats => no card, never an empty one.
 */
/** Null = on: the digest ships enabled and a GM opts OUT (kingdom settings). */
fun digestEnabled(setting: Boolean?): Boolean = setting != false

/** The GM's beat budget, clamped to the plan's 1..6; null = MAX_DIGEST_BEATS. */
fun digestBeatCap(setting: Int?): Int = (setting ?: MAX_DIGEST_BEATS).coerceIn(1, 6)

/** The full End Turn event assembly, extracted so the feed-kind threading is testable. */
fun buildEndTurnDigestEvents(
    caravanEvents: List<CaravanEvent>,
    shipmentEvents: List<CaravanEvent>,
    expeditionChronicle: Array<RawExpeditionChronicleEntry>?,
    preTickThreats: List<RawWarThreat>,
    postTickThreats: List<RawWarThreat>,
    turn: Int,
): List<DigestEvent> =
    caravanDigestEvents(caravanEvents, "caravan", turn) +
        caravanDigestEvents(shipmentEvents, "shipment", turn) +
        expeditionDigestEvents(expeditionChronicle, turn) +
        threatEscalationEvents(preTickThreats, postTickThreats, turn)

/**
 * The dedup baseline from the stored record: only the IMMEDIATELY previous turn's record counts
 * (window 1); an older record means missed turns and a stale baseline, and a malformed record --
 * the flag is an unchecked cast with no migration path -- must read as "no baseline", not throw.
 */
fun digestDedupBaseline(previous: RawDigestRecord?, turn: Int): Set<String> =
    if (previous?.turn == turn - 1) previous.beatIds?.toSet() ?: emptySet() else emptySet()

suspend fun postEndTurnDigest(
    game: Game,
    actor: KingdomActor,
    caravanEvents: List<CaravanEvent>,
    shipmentEvents: List<CaravanEvent>,
    expeditionChronicle: Array<RawExpeditionChronicleEntry>?,
    preTickThreats: List<RawWarThreat>,
    postTickThreats: List<RawWarThreat>,
    turn: Int,
    enabledSetting: Boolean? = null,
    maxBeatsSetting: Int? = null,
) {
    if (!digestEnabled(enabledSetting)) return
    val events = buildEndTurnDigestEvents(
        caravanEvents = caravanEvents,
        shipmentEvents = shipmentEvents,
        expeditionChronicle = expeditionChronicle,
        preTickThreats = preTickThreats,
        postTickThreats = postTickThreats,
        turn = turn,
    )
    val previousBeatIds = runCatching { digestDedupBaseline(actor.lastDigestRecord(), turn) }
        .getOrDefault(emptySet())
    val beats = selectDigestBeats(
        events,
        previousBeatIds,
        cap = digestBeatCap(maxBeatsSetting),
    )
    if (beats.isEmpty()) return
    // This tail runs AFTER the tick is committed; a failure here (missing template in a partial
    // deploy, a rejected flag write) must degrade to a missing card, never abort commitTurn's
    // cleanup.
    runCatching {
        postChatTemplate(
            templatePath = "chatmessages/meanwhile-interlude.hbs",
            templateContext = DigestCardContext(
                title = t("$DIGEST_KEY_PREFIX.title"),
                turnLabel = t("$DIGEST_KEY_PREFIX.turn", recordOf("turn" to turn.toString())),
                beats = beats.map { beat ->
                    t(beat.labelKey, recordOf(*beat.labelArgs.map { (k, v) -> k to v }.toTypedArray()))
                }.toTypedArray(),
            ),
        )
        actor.setAppFlag(
            "lastDigestBeats",
            RawDigestRecord(turn = turn, beatIds = beats.map { it.id }.toTypedArray()),
        )
    }.onFailure { console.error("meanwhile digest failed to post", it) }
}
