package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.Combat
import com.foundryvtt.core.documents.CreateCombatantOptions
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

    // One entry per TOKEN. Each token's data comes from the actor's OWN prototype via
    // getTokenDocument, not a hand-built object: the prototype carries size, bars, vision, name
    // and appendNumber, and a hand-built token spawned every Large creature as a 1x1.
    val tokenData = mutableListOf<AnyObject>()
    var pointIndex = 0
    var skipped = 0
    for (creature in creatures) {
        val repeats = creature.count.coerceAtLeast(0)
        if (repeats == 0) continue
        val worldActor = resolveWorldActor(game, creature.uuid)
        if (worldActor == null) {
            // a manifest can outlive the compendium entry it names; skip the row, keep the rest
            ui.notifications.warn(
                t("camping.encounterStageMissingActor", recordOf("uuid" to creature.uuid))
            )
            skipped += repeats
            continue
        }
        repeat(repeats) {
            val point = points.getOrNull(pointIndex) ?: return@repeat
            pointIndex += 1
            val token = worldActor.getTokenDocument(
                recordOf(
                    "x" to point.x,
                    "y" to point.y,
                    "hidden" to hidden,
                    // hostile: the ring exists to be fought
                    "disposition" to -1,
                    // unlinked, so a wound or an adjustment on one spawned goblin never edits the
                    // world actor every future stage copies from
                    "actorLink" to false,
                ).unsafeCast<AnyObject>()
            ).await()
            tokenData.add(token.asDynamic().toObject().unsafeCast<AnyObject>())
        }
    }
    val created = scene.createEmbeddedDocuments<TokenDocument>("Token", tokenData.toTypedArray())
        .await()
    val spawnedIds = created.mapNotNull { it._id }
    if (spawnedIds.isEmpty()) return null

    // Reuse an active combat when there is one (plan open question 3's stated default): dropping
    // reinforcements into the fight in progress is the common case, and a second combat would
    // orphan the first.
    // `.contents`, NOT `.combats` and NOT `.active`. Verified against the SERVED foundry.mjs:
    //     get combats() { return this.filter(c => (c.scene === null) || (c.scene === game.scenes.current)) }
    //     get active()  { return this.combats.find(c => c.active && (!c.scene || c.scene === game.scenes.current)) }
    // Both are scoped to the VIEWED scene. The previous fix here filtered `.combats` by the spawn
    // scene, which is a no-op whenever the spawn scene is not the one being looked at -- exactly
    // the case it was written for -- so the running fight was missed and a second combat created
    // on top of it. `.contents` is the unfiltered collection.
    val existing = game.combats.contents.firstOrNull { it.active && it.scene?.id == scene.id }
    val combat = existing ?: Combat.create(recordOf("scene" to scene.id)).await()
    if (combat == null) {
        // tokens exist but the fight does not: report the ids so the GM can still undo
        ui.notifications.error(t("camping.encounterStageNoCombat"))
        return StageOutcome(sceneId = scene.id, spawnedTokenIds = spawnedIds, createdCombatId = null)
    }
    if (existing == null) combat.activate().await()
    // WITHOUT the combat option this defaults to game.combats.viewed -- the encounter merely
    // SELECTED in the tracker, which need not be the one resolved above. The tokens then join a
    // different combat than the one this code rolls initiative on.
    TokenDocument.createCombatants(created, CreateCombatantOptions(combat = combat)).await()
    combat.rollNPC().await()
    if (!combat.started) combat.startCombat().await()

    val outcome = StageOutcome(
        sceneId = scene.id,
        spawnedTokenIds = spawnedIds,
        createdCombatId = if (existing == null) combat.id else null,
    )
    // the summary carries the ids, so Undo works from chat after the dialog is long closed --
    // without it StageOutcome had no reachable caller and the undo half was dead code
    postChatTemplate(
        templatePath = "chatmessages/encounter-staged.hbs",
        templateContext = recordOf<String, Any?>(
            "count" to spawnedIds.size,
            "distance" to startDistanceFt,
            "skipped" to skipped,
            "sceneId" to outcome.sceneId,
            "tokenIds" to spawnedIds.joinToString(","),
            "combatId" to outcome.createdCombatId,
        ),
        whisper = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray(),
    )
    return outcome
}

/** Rebuild an outcome from an undo button's data attributes. */
fun stageOutcomeFromDataset(sceneId: String?, tokenIds: String?, combatId: String?): StageOutcome =
    StageOutcome(
        sceneId = sceneId?.takeIf { it.isNotBlank() },
        spawnedTokenIds = tokenIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
        createdCombatId = combatId?.takeIf { it.isNotBlank() },
    )

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

/**
 * The WORLD actor for [uuid], importing a compendium entry the first time it is staged.
 *
 * A token's `actorId` is resolved through `game.actors`, so pointing it at a compendium actor's
 * pack-local id produces a token whose `.actor` is null: no HP, no bars, no sheet, and a
 * combatant with no initiative modifier. Foundry's own compendium drag-drop imports first, and so
 * does this. A previous import is REUSED via `_stats.compendiumSource`, so staging the same
 * bestiary entry every session does not fill the sidebar with copies.
 */
private suspend fun resolveWorldActor(game: Game, uuid: String): PF2EActor? {
    val resolved = fromUuidTypeSafe<PF2EActor>(uuid) ?: return null
    val packId = resolved.pack ?: return resolved
    val existing = game.actors.contents.find {
        it.asDynamic()._stats?.compendiumSource as? String == uuid
    }
    if (existing != null) return existing.unsafeCast<PF2EActor>()
    val pack = game.packs.get(packId) ?: return resolved
    val documentId = resolved.id ?: return resolved
    return runCatching {
        game.actors.importFromCompendium(pack.unsafeCast<com.foundryvtt.core.documents.collections.CompendiumCollection<com.foundryvtt.core.abstract.Document>>(), documentId)
            .await()
            .unsafeCast<PF2EActor>()
    }.getOrNull()
}
