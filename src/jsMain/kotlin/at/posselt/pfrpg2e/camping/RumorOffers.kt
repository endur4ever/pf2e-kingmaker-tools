package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Posts the expiry offers for rumors that just ran out (plan section 6.2), then stamps
 * beatOfferedDay AT POST TIME through the same funnel that aged them.
 *
 * The stamp-at-post is a deliberate deviation from the plan's button-marks-it wording, and it is
 * this week's twice-learned lesson (epithet offers, rival war offers): if only a CLICK stamps,
 * an un-clicked card re-posts on every subsequent tick, forever. Stamped at post, the buttons are
 * free to be what they are -- Beat posts the prose publicly, Quiet just acknowledges -- and "a
 * declined beat never re-offers" holds even for a card nobody clicks.
 *
 * The beat template is chosen HERE, deterministically (id hash + day, no RNG), and pinned on the
 * card -- aging mutates the store between post and click, so the handler must act on what the GM
 * read, not on a recomputed "current" state.
 *
 * Whispered to GM ids and skipped when none exist: an empty whisper array posts PUBLICLY.
 */
suspend fun postRumorExpiryOffers(
    game: Game,
    campingActor: CampingActor,
    expired: List<Rumor>,
    currentDay: Int,
) {
    if (expired.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return
    val tables = runCatching { rumorMutationTablesByCategory() }.getOrDefault(emptyMap())

    for (rumor in expired) {
        // category is not persisted on the rumor; the curator's rumors are all category "rumor"
        // hooks, so the beat table is keyed by what the rumor is ABOUT -- monster is the shipped
        // worked example, and an absent table degrades to a quiet expiry card with no beat button
        val table = tables["monster"] ?: emptyList()
        val seed = rumor.id.hashCode() + currentDay
        val beatKey = selectMutationBeat(rumor, table, seed)

        val ctx = js("{}")
        ctx.campingActorUuid = campingActor.uuid
        ctx.rumorId = rumor.id
        ctx.title = t("camping.rumors.expiredOffer.title")
        ctx.text = rumor.text
        ctx.region = rumor.sourceRegion
        if (beatKey != null) {
            ctx.beatKey = beatKey
            ctx.beatPreview = t(beatKey, recordOf("region" to (rumor.sourceRegion ?: t("camping.rumors.somewhere"))))
            ctx.beatLabel = t("camping.rumors.expiredOffer.postBeat")
        }
        ctx.quietLabel = t("camping.rumors.expiredOffer.letItFade")
        postChatTemplate(
            templatePath = "chatmessages/rumor-expired-offer.hbs",
            templateContext = ctx,
            whisper = gmUserIds,
        )

        // the rescue card, only for leads worth rescuing: a quest-hook rumor about to die gets
        // one last chance to become a quest or a hex hook. The plan lists the card but not its
        // trigger; expiry-of-a-hook is the reading that matches the commissioning card's
        // "ignored-too-long -> consequence offer" without a second whisper for every dead end
        if (rumor.isQuestHook && !rumor.isConverted) {
            val cctx = js("{}")
            cctx.campingActorUuid = campingActor.uuid
            cctx.rumorId = rumor.id
            cctx.title = t("camping.rumors.convertOffer.title")
            cctx.text = rumor.text
            cctx.questLabel = t("camping.rumors.convertOffer.quest")
            if (rumor.sourceHexKey != null) {
                cctx.hexKey = rumor.sourceHexKey
                cctx.hexLabel = t("camping.rumors.convertOffer.hex")
            }
            cctx.dismissLabel = t("camping.rumors.convertOffer.dismiss")
            postChatTemplate(
                templatePath = "chatmessages/rumor-convert-offer.hbs",
                templateContext = cctx,
                whisper = gmUserIds,
            )
        }
    }

    val offeredIds = expired.map { it.id }.toSet()
    campingActor.updateRumors { rumors ->
        rumors.map { if (it.id in offeredIds) it.copy(beatOfferedDay = currentDay) else it }
    }
}
