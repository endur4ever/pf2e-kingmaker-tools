package at.posselt.pfrpg2e.actions

import at.posselt.pfrpg2e.actions.handlers.ActionHandler
import at.posselt.pfrpg2e.actions.handlers.ExecutionMode
import at.posselt.pfrpg2e.actions.handlers.OriginatorPolicy
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
}
