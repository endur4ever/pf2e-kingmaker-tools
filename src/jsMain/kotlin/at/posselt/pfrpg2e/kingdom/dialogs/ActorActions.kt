package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.ImportPlan
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.confirm
import at.posselt.pfrpg2e.app.forms.SimpleApp
import at.posselt.pfrpg2e.app.jsonFilePicker
import at.posselt.pfrpg2e.camping.CampingData
import at.posselt.pfrpg2e.camping.clearCamping
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.clearKingdom
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.migrations.currentSchemaVersion
import at.posselt.pfrpg2e.migrations.migrateCampingDataFrom
import at.posselt.pfrpg2e.migrations.migrateKingdomDataFrom
import at.posselt.pfrpg2e.kingdom.exportCampaignBackup
import at.posselt.pfrpg2e.kingdom.promptRestoreSections
import at.posselt.pfrpg2e.planImport
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.downloadJson
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.game
import com.foundryvtt.core.ui
import com.foundryvtt.pf2e.actor.PF2EParty
import kotlinx.js.JsPlainObject
import js.objects.recordOf
import kotlinx.coroutines.await
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsPlainObject
external interface ActorActionsContext : HandlebarsRenderContext {
    val hasCamping: Boolean
    val hasKingdom: Boolean
}

/**
 * Versioned export envelope for kingdom/camping JSON. The [schemaVersion] lets import run the
 * per-actor migration chain forward from the exported version (migrations key off the world-level
 * schemaVersion setting, not per-actor data, so an unstamped payload would otherwise install
 * stale-schema data that never migrates). [kind] guards against importing a camping file as kingdom.
 */
@JsPlainObject
external interface ExportEnvelope {
    val schemaVersion: Int
    val moduleVersion: String
    val exportedAt: String
    val kind: String
    val data: Any?
}

class ActorActions(
    private val actor: PF2EParty,
) : SimpleApp<ActorActionsContext>(
    title = actor.name,
    template = "applications/settings/actor-actions.hbs",
    id = "kmActorActions",
    classes = setOf("km-actor-actions"),
) {

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "export-kingdom" -> actor.getKingdom()?.let {
                downloadJson(buildEnvelope("kingdom", it), "Kingdom-${actor.uuid}.json")
                close()
            }

            // Whole-campaign backup: world-scoped rather than actor-scoped, but this dialog is the
            // module's existing GM-gated export surface, so it is where a GM already looks.
            "restore-campaign" -> buildPromise {
                if (game.user.isGM) {
                    val json = jsonFilePicker(
                        title = t("kingdom.campaignRestore.title"),
                        label = t("kingdom.campaignRestore.fileLabel"),
                    )
                    val parsed = runCatching { JSON.parse<Any?>(json).asDynamic() }.getOrNull()
                    if (parsed == null) {
                        ui.notifications.error(t("kingdom.campaignRestore.unreadable"))
                    } else {
                        promptRestoreSections(game, parsed)
                    }
                }
                close()
            }

            "export-campaign" -> buildPromise {
                game.exportCampaignBackup()
                close()
            }

            "export-camping" -> actor.getCamping()?.let {
                downloadJson(buildEnvelope("camping", it), "Camping-${actor.uuid}.json")
                close()
            }

            "import-kingdom" -> buildPromise {
                val json = jsonFilePicker(title = t("kingdom.uploadKingdomJson"), t("applications.kingdom"))
                importActorData(
                    json = json,
                    kind = "kingdom",
                    migrate = { from, data -> game.migrateKingdomDataFrom(from, data) },
                    apply = { data -> actor.setKingdom(data.unsafeCast<KingdomData>()) },
                )
                close()
            }

            "import-camping" -> buildPromise {
                val json = jsonFilePicker(title = t("kingdom.uploadCampingJson"), t("applications.camping"))
                importActorData(
                    json = json,
                    kind = "camping",
                    migrate = { from, data -> game.migrateCampingDataFrom(from, data) },
                    apply = { data -> actor.setCamping(data.unsafeCast<CampingData>()) },
                )
                close()
            }

            "reset-kingdom" -> buildPromise {
                if (confirm(t("kingdom.confirmDeleteKingdom", recordOf("actorName" to actor.name)))) {
                    actor.clearKingdom()
                    close()
                }
            }

            "reset-camping" -> buildPromise {
                if (confirm(t("kingdom.confirmDeleteCamping", recordOf("actorName" to actor.name)))) {
                    actor.clearCamping()
                    close()
                }
            }
        }
    }

    private fun buildEnvelope(kind: String, data: Any?): ExportEnvelope =
        ExportEnvelope(
            schemaVersion = game.settings.pfrpg2eKingdomCampingWeather.getSchemaVersion(),
            moduleVersion = (game.modules.get(Config.moduleId)?.asDynamic()?.version as? String) ?: "",
            exportedAt = js("new Date().toISOString()") as String,
            kind = kind,
            data = data,
        )

    /**
     * Parse and apply an imported kingdom/camping JSON payload defensively: malformed JSON produces a
     * toast (never an uncaught throw); a wrong-kind or newer-than-supported envelope is rejected; an
     * older stamped payload is migrated forward via [migrate] before [apply]; an unstamped/too-old
     * payload is imported as-is only after a GM confirmation (re-running the full chain on it could
     * double-transform or reset settings — see [planImport]).
     */
    private suspend fun importActorData(
        json: String,
        kind: String,
        migrate: suspend (fromVersion: Int, data: dynamic) -> Unit,
        apply: suspend (data: dynamic) -> Unit,
    ) {
        // Import overwrites a party actor's entire kingdom or camping state. The dialog is only
        // reachable from a GM-gated actor-directory icon, but players OWN the party actor, so the
        // UI gate is presentation rather than authorization -- the same distinction that left the
        // caravan dispatch handlers open. Guard the mutation itself.
        if (!game.user.isGM) return
        val parsed = runCatching { JSON.parse<Any?>(json).asDynamic() }.getOrNull()
        if (parsed == null || parsed == undefined) {
            ui.notifications.error(t("kingdom.import.parseError"))
            return
        }
        val latest = currentSchemaVersion()
        val hasEnvelope = parsed.kind != undefined && parsed.data != undefined && parsed.schemaVersion != undefined
        if (hasEnvelope && parsed.kind != kind) {
            ui.notifications.error(t("kingdom.import.wrongKind"))
            return
        }
        val stamped = if (hasEnvelope) (parsed.schemaVersion as? Int) else null
        val payload: dynamic = if (hasEnvelope) parsed.data else parsed

        when (val plan = planImport(stamped, latest)) {
            is ImportPlan.RejectNewer -> ui.notifications.error(t("kingdom.import.newerVersion"))
            is ImportPlan.Apply -> {
                apply(payload)
                ui.notifications.info(t("kingdom.import.success"))
            }
            is ImportPlan.Migrate -> {
                migrate(plan.fromVersion, payload)
                apply(payload)
                ui.notifications.info(t("kingdom.import.success"))
            }
            is ImportPlan.ConfirmThenApply -> {
                if (confirm(t("kingdom.import.noVersionStamp"))) {
                    apply(payload)
                    ui.notifications.info(t("kingdom.import.success"))
                }
            }
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<ActorActionsContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        ActorActionsContext(
            partId = parent.partId,
            hasKingdom = actor.getKingdom() != null,
            hasCamping = actor.getCamping() != null,
        )
    }
}