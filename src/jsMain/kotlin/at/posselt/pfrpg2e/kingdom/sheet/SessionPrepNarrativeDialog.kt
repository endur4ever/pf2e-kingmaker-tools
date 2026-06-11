package at.posselt.pfrpg2e.kingdom.sheet

import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.applications.api.DialogV2
import com.foundryvtt.core.applications.api.DialogV2Button
import com.foundryvtt.core.applications.api.WaitOptions
import com.foundryvtt.core.applications.api.Window
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject

/**
 * Simple display dialog that shows the generated session prep narrative.
 */
private interface NarrativeDialogData

object SessionPrepNarrativeDialog {
    suspend fun launch(
        html: String,
        game: Game,
    ) {
        val content = """
            <div class="km-session-prep-narrative">
                $html
            </div>
        """.trimIndent()

        val closeButton = DialogV2Button(
            action = "close",
            label = t("applications.dialog.ok"),
            default = true,
        )

        DialogV2.wait(
            WaitOptions(
                content = content,
                classes = arrayOf("km-dialog-form"),
                window = Window(title = t("kingdom.sessionPrep.generateNarrative")),
                buttons = arrayOf(closeButton),
                rejectClose = false,
            )
        ).await()
    }
}
