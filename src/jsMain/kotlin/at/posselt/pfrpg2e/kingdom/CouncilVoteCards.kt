package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.RawCouncilVote
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game

/**
 * Posts the two messages that open a council vote (plan section 4.1): the ballot goes out PUBLIC
 * and un-whispered -- everyone votes -- and the close/reopen controls go out as a SEPARATE
 * message whispered to the GM ids. The split is load-bearing: chat content is rendered once on
 * the posting client and served frozen to every viewer, so an {{#if isGM}} inside a public card
 * would bake in as true and hand every player a Close button. Whispering is what makes the GM
 * card GM-visible; the isGM bail in its handlers is what makes it authoritative.
 */
suspend fun postCouncilVoteBallot(game: Game, actorUuid: String, vote: RawCouncilVote) {
    val ballotCtx = js("{}")
    ballotCtx.voteId = vote.id
    ballotCtx.actorUuid = actorUuid
    ballotCtx.question = vote.question ?: ""
    ballotCtx.options = vote.options ?: emptyArray<String>()
    postChatTemplate(
        templatePath = "chatmessages/council-vote-ballot.hbs",
        templateContext = ballotCtx,
    )

    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    if (gmUserIds.isEmpty()) return  // an empty whisper array would post the GM controls PUBLICLY
    val gmCtx = js("{}")
    gmCtx.voteId = vote.id
    gmCtx.actorUuid = actorUuid
    gmCtx.question = vote.question ?: ""
    postChatTemplate(
        templatePath = "chatmessages/council-vote-gm-controls.hbs",
        templateContext = gmCtx,
        whisper = gmUserIds,
    )
}

/** Posts the frozen final tally, publicly -- the outcome is the point of the exercise. */
suspend fun postCouncilVoteResult(vote: RawCouncilVote) {
    val tally = tallyVote(vote)
    val ctx = js("{}")
    ctx.title = t("kingdom.councilVotes.resultTitle")
    ctx.question = vote.question ?: ""
    ctx.isTie = tally.isTie
    ctx.tieNote = t("kingdom.councilVotes.tieNote")
    ctx.abstainLabel = t("kingdom.councilVotes.abstain")
    ctx.abstentions = tally.abstentions
    ctx.rows = (vote.options ?: emptyArray()).mapIndexed { index, label ->
        val r = js("{}")
        r.label = label
        r.count = tally.optionCounts.getOrNull(index) ?: 0
        // decidedOption, not leadingOptions: a tie marks NOBODY, because picking a winner out
        // of a tie is exactly the silent decision the GM is supposed to make out loud
        r.isWinner = tally.decidedOption == index
        r
    }.toTypedArray()
    postChatTemplate(
        templatePath = "chatmessages/council-vote-result.hbs",
        templateContext = ctx,
    )
}
