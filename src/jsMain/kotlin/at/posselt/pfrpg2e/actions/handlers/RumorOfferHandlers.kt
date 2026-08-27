package at.posselt.pfrpg2e.actions.handlers

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.RumorState
import at.posselt.pfrpg2e.camping.convertRumorToQuest
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.rumorList
import at.posselt.pfrpg2e.camping.updateRumors
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.kingdom.data.RawHexContent
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import io.github.uuidjs.uuid.v4
import js.objects.recordOf
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RumorOfferData {
    val campingActorUuid: String
    val rumorId: String
    val beatKey: String?
    val hexKey: String?
}

/**
 * Posts an expired rumor's mutation beat publicly. The beat KEY was chosen deterministically and
 * pinned on the card at post time -- the handler renders exactly what the GM previewed, not a
 * re-roll against a store that has aged since.
 */
class PostRumorBeatHandler(private val game: Game) : ActionHandler("postRumorBeat") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<RumorOfferData>()
        val beatKey = data.beatKey ?: return
        val actor = fromUuidTypeSafe<CampingActor>(data.campingActorUuid) ?: return
        val rumor = actor.getCamping()?.rumorList()?.firstOrNull { it.id == data.rumorId } ?: return
        postChatMessage(
            t(beatKey, recordOf("region" to (rumor.sourceRegion ?: t("camping.rumors.somewhere")))),
        )
    }
}

/** Converts an expiring lead into a quest, through the pipeline the preview dialog already uses. */
class ConvertRumorQuestHandler(private val game: Game) : ActionHandler("convertRumorQuest") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<RumorOfferData>()
        val actor = fromUuidTypeSafe<CampingActor>(data.campingActorUuid) ?: return
        val rumor = actor.getCamping()?.rumorList()?.firstOrNull { it.id == data.rumorId } ?: return
        if (rumor.state == RumorState.CONVERTED) return  // idempotent: a stale card converts once
        convertRumorToQuest(game, rumor)
        actor.updateRumors { rumors ->
            rumors.map {
                if (it.id == data.rumorId) it.copy(state = RumorState.CONVERTED, isConverted = true) else it
            }
        }
    }
}

/**
 * Converts an expiring lead into a hex hook -- the net-new path: a RawHexContent lands on the
 * kingdom at the rumor's source hex, GM-visible, so the map remembers what the tavern forgot.
 * The kingdom actor is resolved AT CALL TIME, as the quest conversion already does; the rumor
 * store stays camping-scoped.
 */
class ConvertRumorHexHandler(private val game: Game) : ActionHandler("convertRumorHex") {
    override suspend fun execute(action: ActionMessage, dispatcher: ActionDispatcher) {
        val data = action.data.unsafeCast<RumorOfferData>()
        val hexKey = data.hexKey ?: return
        val actor = fromUuidTypeSafe<CampingActor>(data.campingActorUuid) ?: return
        val rumor = actor.getCamping()?.rumorList()?.firstOrNull { it.id == data.rumorId } ?: return
        if (rumor.state == RumorState.CONVERTED) return
        val kingdomActor = game.getKingdomActors().firstOrNull() ?: return
        val kingdom = kingdomActor.getKingdom() ?: return
        kingdom.hexContents = (kingdom.hexContents ?: emptyArray()) + RawHexContent(
            id = "rumor-hook-${v4()}",
            hexKey = hexKey,
            type = HexContentType.CUSTOM.value,
            name = rumor.text.take(60),
            // HIDDEN, not discovered: the hook is the GM's note that something is out there,
            // and the players earn the discovery on the map, not from an expired rumor card
            visibility = HexContentVisibility.HIDDEN.value,
            gmNotes = rumor.text,
            playerText = "",
        )
        kingdomActor.setKingdom(kingdom)
        actor.updateRumors { rumors ->
            rumors.map {
                if (it.id == data.rumorId) it.copy(state = RumorState.CONVERTED, isConverted = true) else it
            }
        }
    }
}
