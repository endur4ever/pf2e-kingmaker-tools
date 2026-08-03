package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.CrudApplication
import at.posselt.pfrpg2e.app.CrudData
import at.posselt.pfrpg2e.app.CrudItem
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.campaign.CampaignClock
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.t
import js.core.Void
import kotlin.js.Promise

class CampaignClockDialog(
    private val kingdomActor: KingdomActor,
) : CrudApplication(
    title = t("kingdom.manageCampaignClocks"),
    debug = true,
    id = "kmManageCampaignClocks-${kingdomActor.uuid}"
) {
    override fun deleteEntry(id: String) = buildPromise {
        kingdomActor.getKingdom()?.let { kingdom ->
            kingdom.campaignClocks = kingdom.campaignClocks.filter { it.id != id }.toTypedArray()
            kingdomActor.setKingdom(kingdom)
            render()
        }
        undefined
    }

    override fun addEntry(): Promise<Void> = buildPromise {
        ModifyCampaignClock(
            afterSubmit = { clock ->
                kingdomActor.getKingdom()?.let { kingdom ->
                    kingdom.campaignClocks = kingdom.campaignClocks + clock
                    kingdomActor.setKingdom(kingdom)
                }
                render()
            },
        ).launch()
        undefined
    }

    override fun editEntry(id: String) = buildPromise {
        val existing = kingdomActor.getKingdom()?.campaignClocks?.find { it.id == id }
        ModifyCampaignClock(
            data = existing,
            afterSubmit = { clock ->
                kingdomActor.getKingdom()?.let { kingdom ->
                    kingdom.campaignClocks = kingdom.campaignClocks
                        .filter { it.id != clock.id }
                        .toTypedArray() + clock
                    kingdomActor.setKingdom(kingdom)
                }
                render()
            },
        ).launch()
        undefined
    }

    override fun getItems(): Promise<Array<CrudItem>> = buildPromise {
        kingdomActor.getKingdom()?.let { kingdom ->
            kingdom.campaignClocks
                .sortedBy { it.label }
                .map { clock ->
                    CrudItem(
                        id = clock.id,
                        name = clock.label,
                        nameIsHtml = false,
                        additionalColumns = arrayOf(),
                        enable = CheckboxInput(
                            value = clock.active,
                            label = t("applications.enable"),
                            hideLabel = true,
                            name = "enabledIds.${clock.id}",
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
            kingdom.campaignClocks = kingdom.campaignClocks.map { clock ->
                CampaignClock(
                    id = clock.id,
                    label = clock.label,
                    maxTurns = clock.maxTurns,
                    turnsRemaining = clock.turnsRemaining,
                    description = clock.description,
                    pauseOnExpiry = clock.pauseOnExpiry,
                    expired = clock.expired,
                    active = clock.id in enabled,
                    expiryConsequenceUnrest = clock.expiryConsequenceUnrest,
                    expiryMessage = clock.expiryMessage,
                )
            }.toTypedArray()
            kingdomActor.setKingdom(kingdom)
        }
        undefined
    }
}
