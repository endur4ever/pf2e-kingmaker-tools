package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.CrudApplication
import at.posselt.pfrpg2e.app.CrudData
import at.posselt.pfrpg2e.app.CrudItem
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.resting.DAY_SECONDS
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.worldTimeSeconds
import com.foundryvtt.core.game
import js.core.Void
import kotlin.js.Promise

/**
 * Manage-deadlines CRUD (plan SS8), the exact CampaignClockDialog pattern: rows with an enable
 * toggle, add/edit through [ScheduleEventDialog]. GM-only by its launch site -- the Deadlines
 * section that opens it is data-gated to the GM.
 */
class DeadlinesDialog(
    private val kingdomActor: KingdomActor,
) : CrudApplication(
    title = t("kingdom.deadlines.manage"),
    debug = true,
    id = "kmManageDeadlines-${kingdomActor.uuid}",
) {
    private fun currentDay(): Int = game.time.worldTimeSeconds.floorDiv(DAY_SECONDS)

    override fun deleteEntry(id: String) = buildPromise {
        kingdomActor.getKingdom()?.let { kingdom ->
            kingdom.scheduledPressures = (kingdom.scheduledPressures ?: emptyArray())
                .filter { it.id != id }
                .toTypedArray()
            kingdomActor.setKingdom(kingdom)
            render()
        }
        undefined
    }

    override fun addEntry(): Promise<Void> = buildPromise {
        kingdomActor.getKingdom()?.let { kingdom ->
            ScheduleEventDialog(
                kingdom = kingdom,
                currentDay = currentDay(),
                afterSubmit = { raw ->
                    kingdomActor.getKingdom()?.let { current ->
                        current.scheduledPressures = (current.scheduledPressures ?: emptyArray()) + raw
                        kingdomActor.setKingdom(current)
                    }
                    render()
                },
            ).launch()
        }
        undefined
    }

    override fun editEntry(id: String) = buildPromise {
        kingdomActor.getKingdom()?.let { kingdom ->
            val existing = kingdom.scheduledPressures?.find { it.id == id } ?: return@let
            ScheduleEventDialog(
                kingdom = kingdom,
                currentDay = currentDay(),
                existing = existing,
                afterSubmit = { raw ->
                    kingdomActor.getKingdom()?.let { current ->
                        current.scheduledPressures = (current.scheduledPressures ?: emptyArray())
                            .filter { it.id != raw.id }
                            .toTypedArray() + raw
                        kingdomActor.setKingdom(current)
                    }
                    render()
                },
            ).launch()
        }
        undefined
    }

    override fun getItems(): Promise<Array<CrudItem>> = buildPromise {
        kingdomActor.getKingdom()?.let { kingdom ->
            (kingdom.scheduledPressures ?: emptyArray())
                .sortedBy { it.name }
                .map { raw ->
                    CrudItem(
                        id = raw.id,
                        name = raw.name,
                        nameIsHtml = false,
                        additionalColumns = arrayOf(),
                        enable = CheckboxInput(
                            value = raw.active,
                            label = t("applications.enable"),
                            hideLabel = true,
                            name = "enabledIds.${raw.id}",
                        ).toContext(),
                        canBeEdited = true,
                        canBeDeleted = true,
                    )
                }.toTypedArray()
        } ?: emptyArray()
    }

    override fun getHeadings(): Promise<Array<String>> = buildPromise {
        arrayOf()
    }

    override fun onParsedSubmit(value: CrudData): Promise<Void> = buildPromise {
        kingdomActor.getKingdom()?.let { kingdom ->
            val enabled = value.enabledIds.toSet()
            kingdom.scheduledPressures?.forEach { it.active = it.id in enabled }
            kingdomActor.setKingdom(kingdom)
        }
        undefined
    }
}
