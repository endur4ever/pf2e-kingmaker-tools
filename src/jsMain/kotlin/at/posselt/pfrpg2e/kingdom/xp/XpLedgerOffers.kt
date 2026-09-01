package at.posselt.pfrpg2e.kingdom.xp

import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import io.github.uuidjs.uuid.v4
import js.objects.recordOf

/**
 * Automatic XP offers (plan sections 5-6). Entries are created OFFERED silently as beats happen —
 * one chat card per cleared hex would be a dozen cards in an exploration session — and End Turn
 * posts a single digest of everything still unanswered.
 *
 * Nothing here grants XP. The confirm handler does, with an amount the GM can edit first.
 */

/**
 * Record a beat as an unanswered offer, unless this exact (kind, ref) is already in the ledger.
 *
 * Silent by design: the GM meets it in the End Turn digest, not as an interruption mid-session.
 * A GM-only guard would be wrong here — a player completing a quest still earns the party XP —
 * so the write is gated by the funnel, not the caller.
 */
suspend fun Game.proposeXpOffer(
    kind: XpSourceKind,
    sourceRef: String,
    turn: Int,
    tier: XpBeatTier = XpBeatTier.MODERATE,
    note: String? = null,
) {
    if (sourceRef.isBlank()) return
    val party = xpLedgerActor() ?: return
    val amount = defaultXpAward(kind, tier)
    if (amount <= 0) return
    party.updateXpLedger { existing ->
        val candidate = XpLedgerEntry(
            id = v4(),
            turn = turn,
            timestamp = kotlin.js.Date().toISOString(),
            sourceKind = kind,
            sourceRef = sourceRef,
            proposedAmount = amount,
            grantedAmount = null,
            status = XpOfferStatus.OFFERED,
            note = note,
        )
        // proposeEntry is the double-count guard: a hex re-populated and cleared again, or a
        // quest reopened and re-completed, reports the same ref and must not offer twice
        val accepted = proposeEntry(existing, candidate) ?: return@updateXpLedger existing
        appendEntry(existing, accepted)
    }
}

/** One whispered digest per turn listing every offer still unanswered. */
suspend fun postXpLedgerDigest(game: Game, actorUuid: String, turn: Int) {
    val party = game.xpLedgerActor() ?: return
    val pending = pendingOffers(party.xpLedger())
    if (pending.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/xp-ledger-digest.hbs",
        templateContext = recordOf<String, Any?>(
            "actorUuid" to actorUuid,
            "turn" to turn,
            "allIds" to pending.joinToString(",") { it.id },
            "rows" to pending.map { entry ->
                recordOf<String, Any?>(
                    "id" to entry.id,
                    "sourceLabel" to at.posselt.pfrpg2e.kingdom.sheet.contexts.localizeXpSource(entry.sourceKind),
                    "sourceRef" to entry.sourceRef,
                    "amount" to entry.proposedAmount,
                )
            }.toTypedArray(),
        ),
        whisper = gmUserIds,
    )
}
