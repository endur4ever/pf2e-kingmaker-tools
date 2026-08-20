package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.utils.postChatTemplate
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * The condition a starvation band asks the GM to apply.
 *
 * Both bands offer `fatigued` — PF2e has no distinct "starving" condition, and the worsening band
 * escalates by adding harm the GM adjudicates rather than by naming a second condition. Keeping the
 * slug in the card's payload means the handler applies exactly what the button was labelled with.
 */
fun starvationCondition(severity: StarvationSeverity): String? = when (severity) {
    StarvationSeverity.NONE -> null
    StarvationSeverity.FATIGUED, StarvationSeverity.WORSENING -> "fatigued"
}

/**
 * Whispers the GM a confirmation card for campers who crossed a starvation threshold tonight.
 *
 * Nothing is applied here — each button applies exactly its own camper's condition when pressed.
 */
suspend fun postStarvationOffer(game: Game, crossings: List<StarvationCrossing>) {
    if (crossings.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    // An empty whisper array posts PUBLICLY rather than to nobody, which would show players a card
    // of GM-only buttons. With no GM connected there is nobody to confirm anything, so post nothing.
    if (gmUserIds.isEmpty()) return
    val rows = crossings.mapNotNull { crossing ->
        starvationCondition(crossing.severity)?.let { condition ->
            recordOf(
                "uuid" to crossing.actorUuid,
                "name" to crossing.actorName,
                "days" to crossing.daysWithoutFood,
                "condition" to condition,
            )
        }
    }.toTypedArray()
    if (rows.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/starvation-offer.hbs",
        templateContext = recordOf("actors" to rows),
        whisper = gmUserIds,
    )
}

/**
 * Whispers the GM a fatigue offer the night a party marches past its endurance.
 *
 * Posted only on the crossing (see [crossedForcedMarchLimit]), so pushing on for a further week
 * does not re-ask every night. Nothing is applied here; each button applies one camper's fatigue.
 */
suspend fun postForcedMarchOffer(
    game: Game,
    campers: List<Pair<String, String>>,
    days: Int,
    maxDays: Int,
) {
    if (campers.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    // An empty whisper array posts publicly rather than to nobody.
    if (gmUserIds.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/forced-march-offer.hbs",
        templateContext = recordOf(
            "days" to days,
            "maxDays" to maxDays,
            "actors" to campers.map { (uuid, name) -> recordOf("uuid" to uuid, "name" to name) }.toTypedArray(),
        ),
        whisper = gmUserIds,
    )
}
