@file:JsQualifier("foundry.documents")
package com.foundryvtt.core.documents

import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DatabaseDeleteOperation
import com.foundryvtt.core.abstract.DatabaseUpdateOperation
import com.foundryvtt.core.documents.collections.EmbeddedCollection
import com.foundryvtt.core.grid.BaseGrid
import kotlin.js.Promise

external class Scene : ClientDocument {
    companion object : DocumentStatic<Scene>

    override fun delete(operation: DatabaseDeleteOperation): Promise<Scene>
    override fun update(data: AnyObject, operation: DatabaseUpdateOperation): Promise<Scene?>

    var _id: String
    var name: String
    var img: String
    var active: Boolean
    var nagivation: Boolean
    var navOrder: Int
    var navName: String
    var thumb: String
    var width: Int
    var height: Int
    var padding: Double
    var initial: SceneInitial
    var grid: BaseGrid
    var environment: SceneEnvironmentData
    var drawings: EmbeddedCollection<DrawingDocument>
    var tokens: EmbeddedCollection<TokenDocument>
    var tiles: EmbeddedCollection<TileDocument>
    var notes: EmbeddedCollection<NoteDocument>
    var regions: EmbeddedCollection<RegionDocument>
    var levels: EmbeddedCollection<Level>

    // Verified against the served v14.363 Scene schema (2026-09-02): lights, sounds and walls are
    // EmbeddedCollectionFields of BaseAmbientLight / BaseAmbientSound / BaseWall. Measured
    // templates were NOT confirmed in that schema and are deliberately left unbound.
    var lights: EmbeddedCollection<AmbientLightDocument>
    var sounds: EmbeddedCollection<AmbientSoundDocument>
    var walls: EmbeddedCollection<WallDocument>
    var playlist: Playlist?
    var playlistSound: PlaylistSound?
    var firstLevel: Level

    //    var journal: Journal?
    //    var journalEntryPage: JournalEntryPage?
    var weather: String?
    var folder: Folder?
    var ownership: Ownership
    var sort: Int

    val thumbnail: String
    fun activate(): Promise<Unit>
    fun view(): Promise<Unit>
    fun getDimensions(): SceneDimensions
    fun createThumbnail(options: CreateThumbnailOptions = definedExternally): Promise<Thumbnail>
}

