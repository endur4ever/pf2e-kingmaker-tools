package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.subsystems.newlyCrossedThresholds
import at.posselt.pfrpg2e.kingdom.data.RawSubsystemThreshold
import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Offer surface for the influence/research trackers (plan section 5). Offers fire at
 * check-record time -- the plan explicitly has NO tick surface here -- and every card is a
 * whispered GM-confirmed offer; nothing applies mechanically.
 */

/**
 * The thresholds this check newly crossed that still carry an unanswered offer. Answered rows
 * (offerConsumed) stay quiet forever, mirroring RawWarThreat's guard; an unanswered card CAN
 * re-fire if the pool falls below and crosses again, which is the plan's accepted semantics.
 */
fun thresholdsToOffer(
    previous: Int,
    next: Int,
    thresholds: Array<RawSubsystemThreshold>?,
): List<ThresholdOffer> {
    val rows = thresholds ?: return emptyList()
    val crossed = newlyCrossedThresholds(previous, next, rows.map { it.points }).toSet()
    return rows.mapIndexedNotNull { index, row ->
        if (row.points in crossed && row.offerConsumed != true) ThresholdOffer(index, row) else null
    }
}

/**
 * Card identity is the ROW INDEX, not the point value: two rows at the same points each get
 * their own card, and an answer must consume exactly the row its card was minted for.
 */
data class ThresholdOffer(val index: Int, val row: RawSubsystemThreshold)

/** One whispered card per crossed threshold; the effect text rides the card, pinned. */
suspend fun postSubsystemThresholdOffers(
    game: Game,
    storeKind: String,
    entryId: String,
    entryName: String,
    offers: List<ThresholdOffer>,
) {
    if (offers.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    val templatePath = if (storeKind == "influence") {
        "chatmessages/influence-threshold-offer.hbs"
    } else {
        "chatmessages/research-threshold-offer.hbs"
    }
    for (offer in offers) {
        postChatTemplate(
            templatePath = templatePath,
            templateContext = recordOf<String, Any?>(
                "storeKind" to storeKind,
                "entryId" to entryId,
                "entryName" to entryName,
                "thresholdIndex" to offer.index,
                "points" to offer.row.points,
                "effect" to offer.row.effect,
                // authored once, read by every client: the handler re-checks at click time
                "canConvertToQuest" to game.getKingdomActors().isNotEmpty(),
            ),
            whisper = gmUserIds,
        )
    }
}
