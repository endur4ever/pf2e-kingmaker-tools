package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.data.RawNotes
import com.foundryvtt.core.applications.ux.TextEditor.TextEditor
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface NotesContext {
    var rawPublic: String
    var public: String
    var rawGm: String
    var gm: String
}

suspend fun RawNotes.toContext(isGM: Boolean): NotesContext {
    val pub = TextEditor.enrichHTML(public).await()
    // GM-only fields are blanked for players at context-build time (no info leak; mirrors ExpeditionsContext.kt:116).
    val gmRaw = if (isGM) gm else ""
    val gmEnriched = if (isGM) TextEditor.enrichHTML(gm).await() else ""
    return NotesContext(
        rawPublic = public,
        rawGm = gmRaw,
        public = pub,
        gm = gmEnriched,
    )
}