package at.posselt.pfrpg2e.actions

import at.posselt.pfrpg2e.actions.handlers.ActionHandler
import at.posselt.pfrpg2e.actions.handlers.AddHuntAndGatherResultHandler
import at.posselt.pfrpg2e.actions.handlers.ApplyStarvationHandler
import at.posselt.pfrpg2e.actions.handlers.ApplyMealEffectsHandler
import at.posselt.pfrpg2e.actions.handlers.ClearMealEffectsHandler
import at.posselt.pfrpg2e.actions.handlers.ExecutionMode
import at.posselt.pfrpg2e.actions.handlers.GainProvisionsHandler
import at.posselt.pfrpg2e.actions.handlers.LearnSpecialRecipeHandler
import at.posselt.pfrpg2e.actions.handlers.OpenCampingSheetHandler
import at.posselt.pfrpg2e.actions.handlers.OpenKingdomSheetHandler
import at.posselt.pfrpg2e.actions.handlers.OriginatorPolicy
import at.posselt.pfrpg2e.actions.handlers.CastCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.CloseCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.DeleteCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.OpenCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.ReopenCouncilVoteHandler
import at.posselt.pfrpg2e.actions.handlers.SetCouncilVoteNoteHandler
import at.posselt.pfrpg2e.actions.handlers.SyncActivitiesHandler
import at.posselt.pfrpg2e.actions.handlers.SyncBattleOutcomeHandler
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise

class ActionDispatcherSecurityTest {

    private fun runTest(block: suspend () -> Unit): dynamic =
        @Suppress("DELICATE_API_TRANSITIONAL_MINI_MARKER") GlobalScope.promise { block() }

    private class TestActionHandler(
        action: String,
        mode: ExecutionMode,
        originatorPolicy: OriginatorPolicy
    ) : ActionHandler(action, mode, originatorPolicy) {
        var executedCount = 0
        override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
            executedCount++
        }
    }

    private fun mockGame(currentUserId: String, currentIsGm: Boolean, activeGmId: String?, usersList: List<dynamic>): Game {
        val socket = js("({ emit: function(e, d) {} })")
        val users = js("({ get: function(id) { return null; } })")
        
        // Define users lookup map dynamically
        val usersMap = js("({})")
        for (u in usersList) {
            usersMap[u.id] = u
        }
        users.get = { id: String ->
            usersMap[id]
        }
        if (activeGmId != null) {
            users.activeGM = usersMap[activeGmId]
        }

        val gameObj = js("({})")
        gameObj.user = js("({ _id: currentUserId, id: currentUserId, isGM: currentIsGm })")
        gameObj.users = users
        gameObj.socket = socket

        // Mock game.isFirstGM extension logic dependency: users.activeGM?.id == user.id
        // In kotlin, isFirstGM() checks users.activeGM?.id == user.id
        // So this will naturally work if users.activeGM has the correct id.

        return gameObj.unsafeCast<Game>()
    }

    @Test
    fun testGmOnlyActionOriginatedByGmIsAllowed() = runTest {
        val gmUser = js("({ id: 'gm-1', isGM: true, name: 'GM User' })")
        val game = mockGame(currentUserId = "gm-1", currentIsGm = true, activeGmId = "gm-1", usersList = listOf(gmUser))
        
        val handler = TestActionHandler("testAction", ExecutionMode.GM_ONLY, OriginatorPolicy.GM_ONLY)
        val dispatcher = ActionDispatcher(game, listOf(handler), debug = false)

        val action = js("({ action: 'testAction', data: {}, senderId: 'gm-1' })").unsafeCast<ActionMessage>()

        dispatcher.dispatch(action, receivedViaSocket = true)
        assertEquals(1, handler.executedCount)
    }

    @Test
    fun testGmOnlyActionOriginatedByPlayerIsRejected() = runTest {
        val playerUser = js("({ id: 'player-1', isGM: false, name: 'Player User' })")
        val gmUser = js("({ id: 'gm-1', isGM: true, name: 'GM User' })")
        val game = mockGame(currentUserId = "gm-1", currentIsGm = true, activeGmId = "gm-1", usersList = listOf(playerUser, gmUser))
        
        val handler = TestActionHandler("testAction", ExecutionMode.GM_ONLY, OriginatorPolicy.GM_ONLY)
        val dispatcher = ActionDispatcher(game, listOf(handler), debug = false)

        val action = js("({ action: 'testAction', data: {}, senderId: 'player-1' })").unsafeCast<ActionMessage>()

        dispatcher.dispatch(action, receivedViaSocket = true)
        assertEquals(0, handler.executedCount)
    }

    @Test
    fun testAnyActionOriginatedByPlayerIsAllowed() = runTest {
        val playerUser = js("({ id: 'player-1', isGM: false, name: 'Player User' })")
        val gmUser = js("({ id: 'gm-1', isGM: true, name: 'GM User' })")
        val game = mockGame(currentUserId = "gm-1", currentIsGm = true, activeGmId = "gm-1", usersList = listOf(playerUser, gmUser))
        
        val handler = TestActionHandler("testAction", ExecutionMode.GM_ONLY, OriginatorPolicy.ANY)
        val dispatcher = ActionDispatcher(game, listOf(handler), debug = false)

        val action = js("({ action: 'testAction', data: {}, senderId: 'player-1' })").unsafeCast<ActionMessage>()

        dispatcher.dispatch(action, receivedViaSocket = true)
        assertEquals(1, handler.executedCount)
    }

    /** A handler that opts into nothing — proves the default is deny-by-default (fail-closed). */
    private class DefaultPolicyHandler : ActionHandler("defaultPolicyAction") {
        override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {}
    }

    @Test
    fun testOriginatorPolicyIsDenyByDefault() {
        // A newly-added handler that does not set originatorPolicy must be GM_ONLY, so the
        // socket-authorization control stays fail-CLOSED (regression guard for t_000fd424).
        assertEquals(OriginatorPolicy.GM_ONLY, DefaultPolicyHandler().originatorPolicy)
    }

    private class SenderCapturingHandler : ActionHandler(
        "captureSender",
        ExecutionMode.GM_ONLY,
        OriginatorPolicy.ANY,
    ) {
        var capturedSenderId: String? = "not-executed"
        override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
            capturedSenderId = action.senderId
        }
    }

    @Test
    fun testLocalFirstGmDispatchStampsSenderIdBeforeExecute() = runTest {
        // The seam where the first GM's own council ballot silently vanished: dispatch() used to
        // stamp senderId only on the socket-EMIT branches, so the local-execute path (first-GM
        // client, receivedViaSocket = false) reached handlers with senderId undefined and any
        // handler keying its write on it dropped the action without a trace.
        val gmUser = js("({ id: 'gm-1', isGM: true, name: 'GM User' })")
        val game = mockGame(
            currentUserId = "gm-1",
            currentIsGm = true,
            activeGmId = "gm-1",
            usersList = listOf(gmUser),
        )
        val handler = SenderCapturingHandler()
        val dispatcher = ActionDispatcher(game, listOf(handler))
        val action = js("({ action: 'captureSender', data: {} })").unsafeCast<ActionMessage>()
        dispatcher.dispatch(action, receivedViaSocket = false)
        assertEquals("gm-1", handler.capturedSenderId, "the local sender must be stamped, not undefined")
    }

    @Test
    fun testEveryHandlerHasExpectedOriginatorPolicy() {
        // Constructing a handler only stores its constructor args; no game logic runs, so a
        // bare fake Game is safe. This map is the reviewed classification: player-originated
        // camping flows = ANY; GM broadcasts and kingdom/army mutations = GM_ONLY.
        val game = js("({})").unsafeCast<Game>()
        val expected: List<Pair<String, OriginatorPolicy>> = listOf(
            // player-collaborative camping flows — must stay ANY or player camping breaks
            AddHuntAndGatherResultHandler().action to OriginatorPolicy.ANY,
            ApplyMealEffectsHandler(game).action to OriginatorPolicy.ANY,
            ClearMealEffectsHandler().action to OriginatorPolicy.ANY,
            GainProvisionsHandler().action to OriginatorPolicy.ANY,
            LearnSpecialRecipeHandler().action to OriginatorPolicy.ANY,
            SyncActivitiesHandler(game).action to OriginatorPolicy.ANY,
            // council ballots are player-cast by design; execute keys the ballot on the
            // socket-authenticated senderId, never a payload field
            CastCouncilVoteHandler().action to OriginatorPolicy.ANY,
            // GM-only: sheet-push broadcasts + kingdom/army mutation
            OpenCampingSheetHandler(game).action to OriginatorPolicy.GM_ONLY,
            OpenKingdomSheetHandler(game).action to OriginatorPolicy.GM_ONLY,
            SyncBattleOutcomeHandler(game).action to OriginatorPolicy.GM_ONLY,
            // starvation conditions are GM-confirmed; a player must never be able to apply one
            ApplyStarvationHandler().action to OriginatorPolicy.GM_ONLY,
            // closing/reopening a vote is a GM act; they ride the dispatcher only so the write
            // serialises with in-flight ballots on the first-GM client
            CloseCouncilVoteHandler().action to OriginatorPolicy.GM_ONLY,
            ReopenCouncilVoteHandler().action to OriginatorPolicy.GM_ONLY,
            OpenCouncilVoteHandler(game).action to OriginatorPolicy.GM_ONLY,
            DeleteCouncilVoteHandler().action to OriginatorPolicy.GM_ONLY,
            SetCouncilVoteNoteHandler().action to OriginatorPolicy.GM_ONLY,
        )
        val actual: List<Pair<String, OriginatorPolicy>> = listOf(
            AddHuntAndGatherResultHandler(),
            ApplyMealEffectsHandler(game),
            ClearMealEffectsHandler(),
            GainProvisionsHandler(),
            LearnSpecialRecipeHandler(),
            SyncActivitiesHandler(game),
            CastCouncilVoteHandler(),
            OpenCampingSheetHandler(game),
            OpenKingdomSheetHandler(game),
            SyncBattleOutcomeHandler(game),
            ApplyStarvationHandler(),
            CloseCouncilVoteHandler(),
            ReopenCouncilVoteHandler(),
            OpenCouncilVoteHandler(game),
            DeleteCouncilVoteHandler(),
            SetCouncilVoteNoteHandler(),
        ).map { it.action to it.originatorPolicy }
        assertEquals(expected, actual)
    }
}
