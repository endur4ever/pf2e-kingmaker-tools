package at.posselt.pfrpg2e.kingdom.map

import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.createDrawingsResilient
import at.posselt.pfrpg2e.utils.deleteDrawingsResilient
import at.posselt.pfrpg2e.utils.getRealmTileData
import com.foundryvtt.core.Game
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.core.documents.DrawingDocument
import com.foundryvtt.core.documents.onUpdateActor
import com.foundryvtt.core.documents.onUpdateScene
import com.foundryvtt.core.helpers.onCanvasReady
import com.foundryvtt.kingmaker.kingmaker
import js.objects.recordOf
import kotlinx.coroutines.await

const val HEX_CONTENT_DRAWING_TYPE = "hexContent"

/**
 * Find an existing content marker drawing for the given hex key.
 */
private fun findContentMarker(
    drawings: Array<DrawingDocument>,
    hexKey: String,
): DrawingDocument? =
    drawings.find {
        val data = it.getRealmTileData()
        data?.type == HEX_CONTENT_DRAWING_TYPE && data.hexKey == hexKey
    }

/**
 * Sync hex content markers to the active scene.
 * Called after hex content CRUD operations and on scene/actor update hooks.
 */
suspend fun syncHexContentMarkers(game: Game, kingdomActor: KingdomActor) {
    if (!game.settings.pfrpg2eKingdomCampingWeather.getHexMapEnabled()) return
    val activeScene = game.scenes.active ?: return
    if (!activeScene.grid.isHexagonal) return

    val kingdom = kingdomActor.getKingdom() ?: return
    val contents = kingdom.hexContents ?: emptyArray()

    val activeDrawings = activeScene.drawings.contents
    val hexKeysWithContent = contents.map { it.hexKey }.toSet()

    // Delete stale markers (hexes that no longer have any content)
    val staleMarkers = activeDrawings.filter {
        val data = it.getRealmTileData()
        data?.type == HEX_CONTENT_DRAWING_TYPE &&
            data.kingdomActorUuid == kingdomActor.uuid &&
            data.hexKey !in hexKeysWithContent
    }
    if (staleMarkers.isNotEmpty()) {
        activeScene.deleteDrawingsResilient(
            staleMarkers.map { it._id }.toTypedArray()
        )
    }

    // Group contents by hex key (one marker per hex)
    val contentsByHex = contents.groupBy { it.hexKey }

    for ((hexKey, hexContents) in contentsByHex) {
        val existingMarker = findContentMarker(activeScene.drawings.contents, hexKey)

        // Determine the effective visibility for this hex's marker
        val hasVisibleContent = hexContents.any {
            val vis = HexContentVisibility.fromString(it.visibility)
            vis == HexContentVisibility.DISCOVERED || vis == HexContentVisibility.CLEARED
        }
        val hasHiddenContent = hexContents.any {
            val vis = HexContentVisibility.fromString(it.visibility)
            vis == HexContentVisibility.HIDDEN
        }
        val hasPendingEncounter = hexContents.any { it.pendingEncounter == true }

        // Pick the first content for the marker label
        val bestContent = hexContents.firstOrNull() ?: continue

        val markerLabel = if (hexContents.size == 1) {
            bestContent.name
        } else {
            "${bestContent.name} +${hexContents.size - 1}"
        }

        // Determine marker color based on visibility and pending encounter
        val (fillColor, strokeColor) = when {
            hasPendingEncounter -> Pair("#d94a2b", "#a0321a") // Red for pending encounter
            hasVisibleContent -> Pair("#4a90d9", "#2a6099")
            hasHiddenContent -> Pair("#666666", "#444444")
            else -> Pair("#888888", "#666666")
        }

        // Find the hex center point
        val hexObj = kingmaker.region.hexes.find { it.key.toString() == hexKey } ?: continue
        val offset = hexObj.offset
        val point = activeScene.grid.getCenterPoint(offset)

        val markerWidth = 120.0
        val markerHeight = 40.0

        if (existingMarker == null) {
            // Create new marker
            val drawingData = recordOf(
                "shape" to recordOf(
                    "type" to "r",
                    "width" to markerWidth,
                    "height" to markerHeight,
                ),
                "x" to (point.x - markerWidth / 2),
                "y" to (point.y - markerHeight / 2),
                "text" to markerLabel,
                "textAlpha" to 1,
                "fontSize" to 18,
                "textColor" to "#FFFFFF",
                "fillType" to 1,
                "fillColor" to fillColor,
                "fillAlpha" to 0.7,
                "strokeWidth" to 2,
                "strokeColor" to strokeColor,
                "strokeAlpha" to 1.0,
                "locked" to true,
                "flags" to recordOf(
                    "pf2e-kingmaker-tools" to recordOf(
                        "realmTile" to recordOf(
                            "type" to HEX_CONTENT_DRAWING_TYPE,
                            "kingdomActorUuid" to kingdomActor.uuid,
                            "hexKey" to hexKey,
                        )
                    )
                ),
            )
            activeScene.createDrawingsResilient(
                arrayOf(drawingData.unsafeCast<AnyObject>())
            )
        } else {
            // Update existing marker
            if (activeScene.drawings.get(existingMarker._id) != null) {
                existingMarker.update(
                    recordOf<String, Any?>(
                        "text" to markerLabel,
                        "fillColor" to fillColor,
                        "strokeColor" to strokeColor,
                    )
                ).await()
            }
        }
    }
}

/**
 * Register hooks to sync hex content markers on relevant events.
 */
fun registerHexContentSync(game: Game) {
    // Hex-content markers are Drawing documents written to the active scene. Embedded-document
    // creation/deletion is GM-only in Foundry (results replicate to players automatically), so a
    // non-GM client running this sync throws "User X lacks permission to create Drawing". Skip the
    // write hooks entirely for players.
    if (!game.user.isGM) return

    TypedHooks.onUpdateActor { actor, _, _, _ ->
        if (actor is KingdomActor) {
            buildPromise {
                syncHexContentMarkers(game, actor)
            }
        }
    }

    TypedHooks.onUpdateScene { _, _, _, _ ->
        buildPromise {
            val kingdomActor = game.getKingdomActors().firstOrNull() ?: return@buildPromise
            syncHexContentMarkers(game, kingdomActor)
        }
    }

    TypedHooks.onCanvasReady { _ ->
        buildPromise {
            val kingdomActor = game.getKingdomActors().firstOrNull() ?: return@buildPromise
            syncHexContentMarkers(game, kingdomActor)
        }
    }
}
