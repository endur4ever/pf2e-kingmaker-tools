package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawExpeditionChronicleEntry
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface ExpeditionRowContext {
    val id: String
    val title: String
    val activityId: String
    val status: String
    val daysRemaining: Int
    val totalDays: Int
    val progressPercent: Int
    val dc: Int
    val tier: String
    val tierLabel: String
    val outcomeDegree: String?
    val accruedXp: Int
    val accruedInfluenceDelta: Int
    val accruedInjuries: Array<String>
    val lootTier: String?
    val factionStandingDelta: Int
    val targetFactionName: String?
    val factionDeltaLabel: String
    val factionDeltaPositive: Boolean
    val showFactionDelta: Boolean
    val destinationLabel: String?
    val companionIds: Array<String>
    val companionNames: String
    val visibleToPlayers: Boolean
    val rewardApplied: Boolean
    val isResolved: Boolean
    val isInProgress: Boolean
    val isAwaitingResolution: Boolean
    val gmNotes: String
}

@JsPlainObject
external interface ExpeditionsContext {
    val items: Array<ExpeditionRowContext>
    val isGM: Boolean
    val hasExpeditions: Boolean
    val hasAwaitingResolution: Boolean
    val hasInProgress: Boolean
}

fun Array<RawCompanionExpedition>.toExpeditionsContext(
    isGM: Boolean,
    companions: Array<RawCharacter>,
    localize: (String) -> String = { it },
): ExpeditionsContext {
    val companionNameMap = companions.associateBy(
        { it.actorUuid ?: it.name },
        { it.name },
    )
    val items = this
        .filter { it.visibleToPlayers || isGM }
        .map { exp ->
            val progressPercent = if (exp.totalDays > 0) {
                ((exp.totalDays - exp.daysRemaining) * 100 / exp.totalDays).coerceIn(0, 100)
            } else {
                0
            }
            val tierLabel = when (exp.tier) {
                "routine" -> localize("kingdom.expedition.tier.routine")
                "standard" -> localize("kingdom.expedition.tier.standard")
                "perilous" -> localize("kingdom.expedition.tier.perilous")
                else -> exp.tier
            }
            val statusLabel = when (exp.status) {
                "inProgress" -> localize("kingdom.expedition.status.inProgress")
                "awaitingResolution" -> localize("kingdom.expedition.status.awaitingResolution")
                "resolved" -> localize("kingdom.expedition.status.resolved")
                "cancelled" -> localize("kingdom.expedition.status.cancelled")
                else -> exp.status
            }
            ExpeditionRowContext(
                id = exp.id,
                title = exp.title,
                activityId = exp.activityId,
                status = statusLabel,
                daysRemaining = exp.daysRemaining,
                totalDays = exp.totalDays,
                progressPercent = progressPercent,
                // GM-only fields are blanked for the player-facing read-only board (no info leak).
                dc = if (isGM) exp.dc else 0,
                tier = exp.tier,
                tierLabel = tierLabel,
                outcomeDegree = exp.outcomeDegree,
                accruedXp = exp.accruedXp,
                accruedInfluenceDelta = exp.accruedInfluenceDelta,
                accruedInjuries = if (isGM) exp.accruedInjuries else emptyArray(),
                lootTier = exp.lootTier,
                factionStandingDelta = exp.factionStandingDelta,
                // The faction TARGET is public knowledge (the table chose to send the mission);
                // the standing DELTA reveals the outcome, so players only see it once the GM
                // applies the reward.
                targetFactionName = exp.targetFactionName,
                factionDeltaLabel = if (exp.factionStandingDelta > 0) "+${exp.factionStandingDelta}" else "${exp.factionStandingDelta}",
                factionDeltaPositive = exp.factionStandingDelta > 0,
                showFactionDelta = exp.factionStandingDelta != 0 && (isGM || exp.rewardApplied),
                // Destination is public knowledge — the mission was launched to a named place.
                destinationLabel = exp.destinationLabel,
                companionIds = exp.companionIds,
                companionNames = exp.companionIds
                    .mapNotNull { companionNameMap[it] }
                    .joinToString(", "),
                visibleToPlayers = exp.visibleToPlayers,
                rewardApplied = exp.rewardApplied,
                isResolved = exp.status == "resolved",
                isInProgress = exp.status == "inProgress",
                isAwaitingResolution = exp.status == "awaitingResolution",
                gmNotes = if (isGM) exp.gmNotes else "",
            )
        }
        .toTypedArray()
    return ExpeditionsContext(
        items = items,
        isGM = isGM,
        hasExpeditions = items.isNotEmpty(),
        hasAwaitingResolution = items.any { it.isAwaitingResolution },
        hasInProgress = items.any { it.isInProgress },
    )
}

/**
 * True if the companion at [index] is currently on an unresolved expedition.
 * Used to BLOCK companion deletion until expeditions are resolved.
 */
fun KingdomData.companionHasActiveExpedition(index: Int): Boolean {
    val companion = companions?.getOrNull(index) ?: return false
    val key = companion.actorUuid ?: companion.name
    return (companionExpeditions ?: emptyArray()).any { exp ->
        key in exp.companionIds && exp.status != "resolved" && exp.status != "cancelled"
    }
}

/** Anti-abuse cap: the most expeditions allowed in flight at once (design section 4.7). */
const val MAX_CONCURRENT_EXPEDITIONS = 3

/** Count expeditions still in flight (inProgress or awaiting resolution). */
fun activeExpeditionCount(expeditions: Array<RawCompanionExpedition>): Int =
    expeditions.count { it.status == "inProgress" || it.status == "awaitingResolution" }

/** How many resolved/cancelled expedition rows to retain before pruning the oldest. */
const val MAX_RESOLVED_EXPEDITIONS = 50

/**
 * Cap unbounded growth of the expedition log: keep every in-flight expedition
 * (inProgress / awaitingResolution) and only the most recent [cap] terminal
 * (resolved / cancelled) ones, preserving array order. Never drops an active row.
 */
fun pruneResolvedExpeditions(
    expeditions: Array<RawCompanionExpedition>,
    cap: Int = MAX_RESOLVED_EXPEDITIONS,
): Array<RawCompanionExpedition> {
    val terminal = expeditions.filter { it.status == "resolved" || it.status == "cancelled" }
    if (terminal.size <= cap) return expeditions
    val keep = terminal.takeLast(cap).toSet()
    return expeditions.filter { it.status != "resolved" && it.status != "cancelled" || it in keep }.toTypedArray()
}

/**
 * Cap unbounded growth of the expedition chronicle: keep the most recent [cap]
 * entries, dropping oldest. Pure helper for commonTest + jsTest.
 */
const val MAX_EXPEDITION_CHRONICLE_ENTRIES = 100

fun pruneExpeditionChronicle(
    chronicle: Array<RawExpeditionChronicleEntry>?,
    cap: Int = MAX_EXPEDITION_CHRONICLE_ENTRIES,
): Array<RawExpeditionChronicleEntry>? {
    if (chronicle == null || chronicle.isEmpty()) return null
    return if (chronicle.size > cap) chronicle.sliceArray(chronicle.size - cap until chronicle.size) else chronicle
}
