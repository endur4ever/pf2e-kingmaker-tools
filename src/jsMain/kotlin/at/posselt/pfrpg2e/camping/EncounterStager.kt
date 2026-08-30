package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.Combat
import com.foundryvtt.core.documents.Scene
import com.foundryvtt.core.documents.TokenDocument
import com.foundryvtt.core.ui
import com.foundryvtt.pf2e.actor.PF2EActor
import js.objects.recordOf
import kotlinx.coroutines.await

/**
 * The Encounter Stager's Foundry wiring (plan section 3.2): geometry from [ringPlacement],
 * everything else document I/O.
 *
 * A GM button, deliberately NOT a tick -- it hooks neither the monthly turn engine nor the daily
 * clock, so there is no preview/commit parity to honour. It also never calls startCombatTrack:
 * starting the combat drives round 0 -> 1, which the existing combat-track hook already catches,
 * and calling it here would drive the music twice.
 */

/** What a stage created, so [undoStage] can reverse exactly this and nothing else. */
data class StageOutcome(
    /** Nullable because Document.id is: an unsaved scene has none, and undo falls back to active. */
    val sceneId: String?,
    val spawnedTokenIds: List<String>,
    /** Non-null ONLY when this stage created the combat; a reused combat is never deleted. */
    val createdCombatId: String?,
)

/** The scene a stage happens on: the active one, since that is where the players are looking. */
private fun stageScene(game: Game): Scene? = game.scenes.active

/**
 * Spawn the manifest in a ring around the party token, add the tokens to a combat and roll
 * initiative. Returns null (having notified the GM why) when it cannot run.
 */
suspend fun stageEncounter(
    game: Game,
    partyActor: CampingActor,
    manifest: RawEncounterManifest?,
    startDistanceFt: Int,
    hidden: Boolean,
): StageOutcome? {
    if (!game.user.isGM) return null
    val creatures = manifest.creatureList()
    val total = manifest.spawnCount()
    if (creatures.isEmpty() || total <= 0) {
        ui.notifications.warn(t("camping.encounterStageNoCreatures"))
        return null
    }
    val scene = stageScene(game)
    if (scene == null) {
        ui.notifications.error(t("camping.encounterStageNoScene"))
        return null
    }
    val partyToken = scene.tokens.contents.find { it.actorId == partyActor.id }
    if (partyToken == null) {
        ui.notifications.error(t("camping.encounterStageNoParty"))
        return null
    }

    val grid = scene.grid
    val center = StagePoint(
        x = partyToken.x + grid.sizeX / 2.0,
        y = partyToken.y + grid.sizeY / 2.0,
    )
    val radius = feetToPixels(
        distanceFt = startDistanceFt.toDouble(),
        gridDistanceFt = grid.distance.toDouble(),
        gridSizePx = grid.size.toDouble(),
    )
    val points = ringPlacement(
        center = center,
        radiusPx = radius,
        count = total,
        gridSizePx = grid.size.toDouble(),
    )

    // one entry per TOKEN, so a count of 3 resolves its actor once and reuses the prototype
    val tokenData = mutableListOf<AnyObject>()
    var pointIndex = 0
    for (creature in creatures) {
        val repeats = creature.count.coerceAtLeast(0)
        if (repeats == 0) continue
        val actor = fromUuidTypeSafe<PF2EActor>(creature.uuid)
        if (actor == null) {
            // a manifest can outlive the compendium entry it names; skip the row, keep the rest
            ui.notifications.warn(
                t("camping.encounterStageMissingActor", recordOf("uuid" to creature.uuid))
            )
            pointIndex += repeats
            continue
        }
        repeat(repeats) {
            val point = points.getOrNull(pointIndex) ?: return@repeat
            pointIndex += 1
            val data = js("({})").unsafeCast<AnyObject>()
            val dyn = data.asDynamic()
            dyn.name = actor.name
            dyn.actorId = actor.id
            // unlinked: an adjustment or a wound on one spawned goblin must not edit the
            // bestiary entry every future encounter draws from
            dyn.actorLink = false
            dyn.x = point.x
            dyn.y = point.y
            dyn.hidden = hidden
            dyn.disposition = -1
            dyn.texture = actor.prototypeToken.texture
            tokenData.add(data)
        }
    }
    if (tokenData.isEmpty()) {
        ui.notifications.warn(t("camping.encounterStageNoCreatures"))
        return null
    }

    val created = scene.createEmbeddedDocuments<TokenDocument>("Token", tokenData.toTypedArray())
        .await()
    val spawnedIds = created.mapNotNull { it._id }
    if (spawnedIds.isEmpty()) return null

    // Reuse an active combat when there is one (plan open question 3's stated default): dropping
    // reinforcements into the fight in progress is the common case, and a second combat would
    // orphan the first.
    val existing = game.combats.active
    val combat = existing ?: Combat.create(recordOf("scene" to scene.id)).await()
    if (combat == null) {
        // tokens exist but the fight does not: report the ids so the GM can still undo
        ui.notifications.error(t("camping.encounterStageNoCombat"))
        return StageOutcome(sceneId = scene.id, spawnedTokenIds = spawnedIds, createdCombatId = null)
    }
    if (existing == null) combat.activate().await()
    TokenDocument.createCombatants(created).await()
    combat.rollNPC().await()
    if (!combat.started) combat.startCombat().await()

    postChatMessage(
        t(
            "camping.encounterStaged",
            recordOf("count" to total, "distance" to startDistanceFt),
        ),
        whisper = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray(),
    )
    return StageOutcome(
        sceneId = scene.id,
        spawnedTokenIds = spawnedIds,
        createdCombatId = if (existing == null) combat.id else null,
    )
}

/**
 * Reverse a [StageOutcome]: delete exactly the tokens this stage spawned, then delete the combat
 * ONLY if this stage created it -- a combat that was already running belongs to the fight the GM
 * was already in.
 */
suspend fun undoStage(game: Game, outcome: StageOutcome) {
    if (!game.user.isGM) return
    val scene = outcome.sceneId?.let { game.scenes.get(it) } ?: stageScene(game) ?: return
    val present = outcome.spawnedTokenIds.filter { id -> scene.tokens.contents.any { it._id == id } }
    if (present.isNotEmpty()) {
        scene.deleteEmbeddedDocuments<TokenDocument>("Token", present.toTypedArray()).await()
    }
    outcome.createdCombatId?.let { id ->
        game.combats.get(id)?.delete()?.await()
    }
    ui.notifications.info(t("camping.encounterStageUndo"))
}
