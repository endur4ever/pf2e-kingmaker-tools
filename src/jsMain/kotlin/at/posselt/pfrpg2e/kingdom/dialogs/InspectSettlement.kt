package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.data.kingdom.settlementPurchaseAccessLevels
import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.HiddenInput
import at.posselt.pfrpg2e.app.forms.NumberInput
import at.posselt.pfrpg2e.app.forms.OverrideType
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.data.ValueEnum
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementLayoutType
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementType
import at.posselt.pfrpg2e.data.kingdom.settlements.npcOccupations
import at.posselt.pfrpg2e.data.kingdom.settlements.growPopulation
import at.posselt.pfrpg2e.data.kingdom.settlements.recommendedRosterSize
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.data.kingdom.structures.calculateAvailableItems
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.ChosenFeat
import at.posselt.pfrpg2e.kingdom.getAllActivities
import at.posselt.pfrpg2e.kingdom.sheet.contexts.NavEntryContext
import at.posselt.pfrpg2e.kingdom.sheet.contexts.createTabs
import at.posselt.pfrpg2e.kingdom.structures.RawNpcEntry
import at.posselt.pfrpg2e.kingdom.structures.RawPopulationRoster
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.kingdom.structures.RawSettlement
import at.posselt.pfrpg2e.kingdom.structures.isStructure
import at.posselt.pfrpg2e.kingdom.SettlementTerrain
import at.posselt.pfrpg2e.kingdom.structures.parseSettlement
import at.posselt.pfrpg2e.kingdom.structures.toRaw
import at.posselt.pfrpg2e.localization.Translatable
import at.posselt.pfrpg2e.toCamelCase
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.buildUuid
import at.posselt.pfrpg2e.utils.formatAsModifier
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.toRecord
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.applications.ux.TextEditor.TextEditor
import com.foundryvtt.core.data.dsl.buildSchema
import com.foundryvtt.core.documents.onCreateTile
import com.foundryvtt.core.documents.onCreateToken
import com.foundryvtt.core.documents.onDeleteScene
import com.foundryvtt.core.documents.onDeleteTile
import com.foundryvtt.core.documents.onDeleteToken
import com.foundryvtt.core.documents.onUpdateTile
import com.foundryvtt.core.documents.onUpdateToken
import com.foundryvtt.core.helpers.onApplyTokenStatusEffect
import com.foundryvtt.core.ui
import js.core.Void
import js.objects.ReadonlyRecord
import js.objects.recordOf
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise
import at.posselt.pfrpg2e.kingdom.appliesTo
import at.posselt.pfrpg2e.kingdom.ACCESS_BENEFIT_CRAFTING
import at.posselt.pfrpg2e.kingdom.ACCESS_BENEFIT_TRAINER
import at.posselt.pfrpg2e.kingdom.unionSettlementAccess
import at.posselt.pfrpg2e.kingdom.accessGrantList
import at.posselt.pfrpg2e.data.kingdom.settlements.CRAFTING_ACCESS_SOURCES
import at.posselt.pfrpg2e.data.kingdom.settlements.TRAINER_ACCESS_SOURCES
import at.posselt.pfrpg2e.data.kingdom.settlements.AccessSource

@JsPlainObject
external interface LabelValueContext {
    val label: String
    val value: Int
}


@Suppress("unused")
@JsPlainObject
external interface InspectSettlementContext : ValidatedHandlebarsContext {
    val blocksInput: FormElementContext
    val typeInput: FormElementContext
    val secondaryTerritoryInput: FormElementContext
    val manualSettlementLevelInput: FormElementContext
    val waterBordersInput: FormElementContext
    val layoutInput: FormElementContext
    val terrainInput: FormElementContext
    val hexKeyInput: FormElementContext
    val level: Int
    val type: String
    val manualSettlementLevel: Boolean
    val lacksBridge: Boolean
    val isOvercrowded: Boolean
    val residentialLots: Int
    val consumption: Int
    val consumptionSurplus: Int
    val influence: Int
    val blocks: Int
    val maximumBlocks: String
    val maxItemBonus: Int
    val population: String
    val allowCapitalInvestment: Boolean
    val structures: Array<LabelValueContext>
    val bonuses: Array<String>
    val notes: Array<String>
    val availableItems: ReadonlyRecord<String, String>
    val currentTab: String
    val tabs: Array<NavEntryContext>
    val storage: Array<LabelValueContext>
    val settlementActions: Int
    val populationNpcs: Array<RawNpcEntry>
    val itemPurchaseLevel: Int
    val trainers: Array<String>
    val craftingAccess: Array<String>
}

@JsPlainObject
external interface InspectSettlementData {
    val blocks: Int
    val type: String
    val secondaryTerritory: Boolean
    val manualSettlementLevel: Boolean
    val waterBorders: Int
    val layoutType: String
    val terrain: String?
    val hexKey: String?
}

@JsExport
class InspectSettlementDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            int("blocks")
            enum<SettlementType>("type")
            enum<SettlementLayoutType>("layoutType")
            enum<SettlementTerrain>("terrain", nullable = true)
            boolean("secondaryTerritory")
            boolean("manualSettlementLevel")
            int("waterBorders")
            string("hexKey", nullable = true)
        }
    }
}

@Suppress("unused")
enum class SettlementNav : Translatable, ValueEnum {
    STATUS,
    SHOPPING,
    STRUCTURES,
    STORAGE,
    NOTES,
    BONUSES,
    POPULATION;

    companion object {
        fun fromString(value: String) = fromCamelCase<SettlementNav>(value)
    }

    override val value: String
        get() = toCamelCase()

    override val i18nKey: String
        get() = "settlementNav.$value"
}


class InspectSettlement(
    private val game: Game,
    private val kingdom: KingdomData,
    title: String,
    private val autoCalculateSettlementLevel: Boolean,
    private val allStructuresStack: Boolean,
    private val allowCapitalInvestmentInCapitalWithoutBank: Boolean,
    private val capStructureBonusAtKingdomLevel: Boolean,
    private val kingdomLevel: Int,
    settlement: RawSettlement,
    feats: List<ChosenFeat>,
    private val onRosterChange: suspend (roster: RawPopulationRoster) -> Unit,
    private val afterSubmit: suspend (settlement: RawSettlement) -> Unit
) : FormApp<InspectSettlementContext, InspectSettlementData>(
    title = title,
    template = "applications/kingdom/settlement.hbs",
    debug = true,
    classes = setOf("km-inspect-settlement"),
    dataModel = InspectSettlementDataModel::class.js,
    id = "kmInspectSettlement-${settlement.sceneId}",
    width = 700,
) {
    init {
        appHook.onDeleteScene { _, _, _ -> render() }
        appHook.onCreateTile { _, _, _ -> render() }
        appHook.onUpdateTile { _, _, _, _ -> render() }
        appHook.onDeleteTile { _, _, _ -> render() }
        appHook.onDeleteToken { token, _, _ ->
            if (token.isStructure()) {
                render()
            }
        }
        appHook.onUpdateToken { token, _, _, _ ->
            if (token.isStructure()) {
                render()
            }
        }
        appHook.onCreateToken { token, _, _ ->
            if (token.isStructure()) {
                render()
            }
        }
        appHook.onApplyTokenStatusEffect { _, _, _ -> render() }
    }

    val magicItemLevelIncreases = feats.sumOf { it.feat.settlementMagicItemLevelIncrease ?: 0 }
    var currentNav = SettlementNav.STATUS
    var current = RawSettlement(
        sceneId = settlement.sceneId,
        lots = settlement.lots,
        level = settlement.level,
        type = settlement.type,
        secondaryTerritory = settlement.secondaryTerritory,
        manualSettlementLevel = settlement.manualSettlementLevel,
        waterBorders = settlement.waterBorders,
        layoutType = settlement.layoutType,
        terrain = settlement.terrain,
        hexKey = settlement.hexKey,
        populationRoster = settlement.populationRoster ?: RawPopulationRoster(),
        // siege state the dialog never renders: rebuilding without it un-razes every structure
        // a siege destroyed the moment the GM opens and saves this dialog
        destroyedStructureIds = settlement.destroyedStructureIds,
    )

    init {
        // The seeded starter roster only exists on the evaluated Settlement;
        // copy it into the editable raw data when the stored roster is empty
        // so it renders in the population tab and persists on save. Done once
        // at construction so deleting NPCs in the open dialog doesn't reseed.
        if (current.populationRoster?.npcs?.isNotEmpty() != true) {
            game.scenes.get(current.sceneId)?.parseSettlement(
                rawSettlement = current,
                autoCalculateSettlementLevel = autoCalculateSettlementLevel,
                allStructuresStack = allStructuresStack,
                allowCapitalInvestmentInCapitalWithoutBank = allowCapitalInvestmentInCapitalWithoutBank,
                capStructureBonusAtKingdomLevel = capStructureBonusAtKingdomLevel,
                kingdomLevel = kingdomLevel,
            )?.let { parsed ->
                current.populationRoster = parsed.populationRoster.toRaw()
            }
        }
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "change-nav" -> {
                event.preventDefault()
                event.stopPropagation()
                target.dataset["link"]
                    ?.let { SettlementNav.fromString(it) }
                    ?.let { currentNav = it }
                render()
            }

            "km-save" -> buildPromise {
                if (isValid()) {
                    close().await()
                    afterSubmit(current)
                }
                undefined
            }

            "generate-npcs" -> buildPromise {
                // Tops the roster up to the recommended size for the settlement's current
                // population. Only ever adds entries: user edits and deletions stick.
                val parsed = game.scenes.get(current.sceneId)?.parseSettlement(
                    rawSettlement = current,
                    autoCalculateSettlementLevel = autoCalculateSettlementLevel,
                    allStructuresStack = allStructuresStack,
                    allowCapitalInvestmentInCapitalWithoutBank = allowCapitalInvestmentInCapitalWithoutBank,
                    capStructureBonusAtKingdomLevel = capStructureBonusAtKingdomLevel,
                    kingdomLevel = kingdomLevel,
                )
                if (parsed != null) {
                    val grown = parsed.growPopulation()
                    if (grown.npcs.size > (current.populationRoster?.npcs?.size ?: 0)) {
                        // the model round trip has no memory fields, so carry them across or
                        // the top-up erases every tracked resident's ledger
                        val updatedRoster = at.posselt.pfrpg2e.kingdom.mergeNpcMemoryFields(
                            grown.toRaw(),
                            current.populationRoster,
                        )
                        current.populationRoster = updatedRoster
                        onRosterChange(updatedRoster)
                        render()
                    } else {
                        ui.notifications.info(
                            t(
                                "kingdom.population.rosterAtRecommendedSize",
                                recordOf("count" to parsed.recommendedRosterSize()),
                            )
                        )
                    }
                }
                undefined
            }

            "add-npc" -> buildPromise {
                PopulationAddDialog(
                    occupations = npcOccupations.toTypedArray(),
                    onAdd = { npc ->
                        val roster = current.populationRoster ?: RawPopulationRoster()
                        val existingNpcs = roster.npcs?.toMutableList() ?: mutableListOf()
                        existingNpcs.add(npc)
                        val updatedRoster = RawPopulationRoster(npcs = existingNpcs.toTypedArray())
                        current.populationRoster = updatedRoster
                        onRosterChange(updatedRoster)
                        render()
                    },
                ).launch()
                undefined
            }

            "edit-npc" -> buildPromise {
                val index = target.dataset["index"]?.toIntOrNull()
                if (index != null) {
                    val roster = current.populationRoster ?: RawPopulationRoster()
                    val npcs = roster.npcs ?: emptyArray()
                    if (index in npcs.indices) {
                        val existing = npcs[index]
                        PopulationEditDialog(
                            occupations = npcOccupations.toTypedArray(),
                            existing = existing,
                            otherTrackedCount = at.posselt.pfrpg2e.kingdom.countTrackedNpcs(kingdom) -
                                (if (existing.memoryTracked == true) 1 else 0),
                            trackedNames = at.posselt.pfrpg2e.kingdom.trackedNpcNames(kingdom),
                            isGM = game.user.isGM,
                            onSave = { updated ->
                                val existingNpcs = (roster.npcs?.toMutableList() ?: mutableListOf())
                                existingNpcs[index] = updated
                                val updatedRoster = RawPopulationRoster(npcs = existingNpcs.toTypedArray())
                                current.populationRoster = updatedRoster
                                onRosterChange(updatedRoster)
                                render()
                            },
                            onDelete = {
                                val existingNpcs = (roster.npcs?.toMutableList() ?: mutableListOf())
                                existingNpcs.removeAt(index)
                                val updatedRoster = RawPopulationRoster(npcs = existingNpcs.toTypedArray())
                                current.populationRoster = updatedRoster
                                onRosterChange(updatedRoster)
                                render()
                            },
                        ).launch()
                    }
                }
                undefined
            }

            "delete-npc" -> buildPromise {
                val index = target.dataset["index"]?.toIntOrNull()
                if (index != null) {
                    val roster = current.populationRoster ?: RawPopulationRoster()
                    val existingNpcs = (roster.npcs?.toMutableList() ?: mutableListOf())
                    existingNpcs.removeAt(index)
                    val updatedRoster = RawPopulationRoster(npcs = existingNpcs.toTypedArray())
                    current.populationRoster = updatedRoster
                    onRosterChange(updatedRoster)
                    render()
                }
                undefined
            }
        }
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<InspectSettlementContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val parsed = game.scenes.get(current.sceneId)?.parseSettlement(
            rawSettlement = current,
            autoCalculateSettlementLevel = autoCalculateSettlementLevel,
            allStructuresStack = allStructuresStack,
            allowCapitalInvestmentInCapitalWithoutBank = allowCapitalInvestmentInCapitalWithoutBank,
            capStructureBonusAtKingdomLevel = capStructureBonusAtKingdomLevel,
            kingdomLevel = kingdomLevel,
        )
        checkNotNull(parsed) {
            val msg = t("kingdom.settlementSceneDeleted")
            ui.notifications.error(msg)
            msg
        }
        val manualSettlementLevel = current.manualSettlementLevel == true
        val blocksInput = if (manualSettlementLevel) {
            NumberInput(
                name = "blocks",
                label = t("kingdom.blocks"),
                value = current.lots,
                hideLabel = true,
            )
        } else {
            HiddenInput(
                name = "blocks",
                value = current.lots.toString(),
                overrideType = OverrideType.NUMBER,
            )
        }
        val typeInput = Select.fromEnum<SettlementType>(
            name = "type",
            value = SettlementType.fromString(current.type) ?: SettlementType.SETTLEMENT,
            hideLabel = true,
        )
        val secondaryTerritoryInput = CheckboxInput(
            name = "secondaryTerritory",
            label = t("kingdom.secondaryTerritory"),
            value = current.secondaryTerritory,
            hideLabel = true,
        )
        val settlementLayout = Select.fromEnum<SettlementLayoutType>(
            name = "layoutType",
            label = t("enums.settlementLayoutType"),
            value = SettlementLayoutType.fromString(current.layoutType),
            hideLabel = true,
        )
        val terrainSelect = Select.fromEnum<SettlementTerrain>(
            name = "terrain",
            label = t("kingdom.terrain"),
            value = current.terrain?.let { SettlementTerrain.fromString(it) },
            hideLabel = true,
            required = false,
        )
        val manualSettlementLevelInput = CheckboxInput(
            name = "manualSettlementLevel",
            label = t("kingdom.manualManagement"),
            value = manualSettlementLevel,
            hideLabel = true,
        )
        val waterBordersInput = NumberInput(
            name = "waterBorders",
            label = t("kingdom.waterBorders"),
            value = current.waterBorders,
            hideLabel = true,
        )
        val hexKeyInput = TextInput(
            name = "hexKey",
            label = t("kingdom.caravans.settlementHex"),
            value = current.hexKey,
            required = false,
            help = t("kingdom.caravans.settlementHexHelp"),
            hideLabel = true,
        )
        val blacklist = (kingdom.structureBlacklist ?: emptyArray()).toSet()
        val settlementStructures = parsed.constructedStructures
            .filter { it.id !in blacklist }
            .groupBy { it.id }
            .values
            .sortedBy { it.first().name }
            .map { instances ->
                async {
                    val instance = instances.first()
                    TextEditor.enrichHTML(buildUuid(instance.uuid, instance.name)).await() to instances.size
                }
            }
            .awaitAll()
            .map { LabelValueContext(label = it.first, value = it.second) }
            .toTypedArray()
        val activitiesById = kingdom.getAllActivities().associateBy { it.id }
        val bonuses = parsed.highestUniqueBonuses
            .mapNotNull { bonus ->
                val activity = bonus.activity
                val skill = bonus.skill
                val mod = bonus.value.formatAsModifier()
                if (activity != null && skill != null) {
                    t(
                        "kingdom.bonusToActivityUsingSkill", recordOf(
                            "modifier" to mod,
                            "activity" to (activitiesById[activity]?.title ?: ""),
                            "skill" to t(skill)
                        )
                    )
                } else if (skill != null) {
                    t(
                        "kingdom.bonusTo", recordOf(
                            "modifier" to mod,
                            "selector" to t(skill),
                        )
                    )
                } else if (activity != null) {
                    t(
                        "kingdom.bonusTo", recordOf(
                            "modifier" to mod,
                            "selector" to (activitiesById[activity]?.title ?: ""),
                        )
                    )
                } else {
                    null
                }
            }
            .toTypedArray()
        val parsedStorage = parsed.constructedStructures.map { it.storage }
            .fold(CommodityStorage()) { prev, curr -> prev + curr }
        val storage = listOf(
            t("kingdom.food") to parsedStorage.food,
            t("kingdom.lumber") to parsedStorage.lumber,
            t("kingdom.luxuries") to parsedStorage.luxuries,
            t("kingdom.ore") to parsedStorage.ore,
            t("kingdom.stone") to parsedStorage.stone,
        )
            .filter { it.second > 0 }
            .map { LabelValueContext(label = it.first, value = it.second) }
            .toTypedArray()
        // Quest-granted access unions with the structure-derived access below. The item level is
        // unioned HERE, before calculateAvailableItems consumes it, so a granted purchase level
        // actually widens what the settlement can buy rather than only changing a displayed number.
        val questGrants = kingdom.accessGrantList()
        val grantedAccess = unionSettlementAccess(
            settlementId = current.sceneId,
            baseTrainers = parsed.trainers,
            baseCrafting = parsed.craftingAccess,
            baseItemLevel = parsed.itemPurchaseLevel,
            grants = questGrants,
        )
        // Renown perk: the realm's shops stock better goods when someone the realm reveres walks
        // in. SETTLEMENT-scoped, not per-shopper -- this dialog has no viewer, no user and no PC
        // in scope, so "whose renown applies" has no answer here; the GM granted every tier by
        // hand, so the max across the party is the legible reading ("our reputation opens doors").
        // Unioned at the same seam the quest grants use, BEFORE calculateAvailableItems, so the
        // nudge actually widens what can be bought rather than only changing a printed number.
        val renownAccessLevels = settlementPurchaseAccessLevels(
            (kingdom.renown ?: emptyArray()).map { it.purchaseAccessTier ?: 0 }
        )
        val basePurchaseLevel = grantedAccess.itemLevel + renownAccessLevels
        val availableItems = calculateAvailableItems(
            settlementLevel = basePurchaseLevel,
            preventItemLevelPenalty = parsed.preventItemLevelPenalty,
            magicalItemLevelIncrease = magicItemLevelIncreases,
            bonuses = parsed.availableItems,
        ).toEntries().map { (group, amount) ->
            t(group) to if (amount >= 0) {
                t("kingdom.availableItemLevels", recordOf("itemLevel" to amount))
            } else {
                t("kingdom.notAvailable")
            }
        }.toRecord()

        // Rendered from the SAME table Settlement.trainers/craftingAccess fold over, so the list a
        // player reads and the list the quest-grant union dedupes against cannot drift apart. Each
        // row names every structure that granted it, which also fixes a settlement holding both a
        // structure and its -vk variant showing only one of the two names.
        fun accessLines(sources: List<AccessSource>, i18nPrefix: String): MutableList<String> =
            sources.mapNotNull { source ->
                val granting = parsed.constructedStructures
                    .filter { source.matches(it.id.removeSuffix("-vk")) }
                if (granting.isEmpty()) return@mapNotNull null
                val names = granting.map { it.name }.distinct().joinToString(" / ")
                val benefits = source.benefits.joinToString(", ") { t("$i18nPrefix.$it") }
                "$names: $benefits"
            }.toMutableList()

        val trainersList = accessLines(TRAINER_ACCESS_SOURCES, "kingdom.class")
        val craftingList = accessLines(CRAFTING_ACCESS_SOURCES, "kingdom.crafting")

        // Structure-derived lines are pre-composed display strings ("Shrine: Cleric, Oracle"),
        // while grants are bare ids, so dedup is done on the bare-id lists that unionSettlementAccess
        // works with: a quest granting a trainer the settlement already has adds nothing, and only
        // the genuinely new benefits get their own line naming the quest that granted them.
        val questTitles = (kingdom.quests ?: emptyArray()).associate { it.id to it.title }
        // The granted value is free text the GM typed, so it is shown verbatim rather than run
        // through a localization key that would render raw for anything unexpected.
        fun grantedExtras(type: String, alreadyProvided: List<String>) =
            questGrants
                .filter { it.appliesTo(current.sceneId) && it.benefitType == type }
                .mapNotNull { grant -> grant.value?.let { grant to it } }
                .filterNot { (_, value) -> value in alreadyProvided }
                .map { (grant, value) ->
                    t(
                        "kingdom.settlementGrantedBy",
                        recordOf(
                            "benefit" to value,
                            "quest" to (questTitles[grant.sourceQuestId] ?: grant.sourceQuestId),
                        ),
                    )
                }
        trainersList.addAll(grantedExtras(ACCESS_BENEFIT_TRAINER, parsed.trainers))
        craftingList.addAll(grantedExtras(ACCESS_BENEFIT_CRAFTING, parsed.craftingAccess))

        val notes = parsed.notes.toTypedArray()
        InspectSettlementContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            manualSettlementLevel = manualSettlementLevel,
            blocksInput = blocksInput.toContext(),
            typeInput = typeInput.toContext(),
            secondaryTerritoryInput = secondaryTerritoryInput.toContext(),
            manualSettlementLevelInput = manualSettlementLevelInput.toContext(),
            waterBordersInput = waterBordersInput.toContext(),
            blocks = parsed.occupiedBlocks,
            level = parsed.level,
            type = t(parsed.type),
            lacksBridge = parsed.lacksBridge,
            isOvercrowded = parsed.isOvercrowded,
            residentialLots = parsed.residentialLots,
            consumption = parsed.consumption,
            consumptionSurplus = parsed.consumptionSurplus,
            influence = parsed.size.influence,
            maximumBlocks = parsed.size.maximumBlocks,
            maxItemBonus = parsed.size.maxItemBonus,
            population = parsed.size.population,
            allowCapitalInvestment = parsed.allowCapitalInvestment,
            structures = settlementStructures,
            bonuses = bonuses,
            notes = notes,
            settlementActions = parsed.settlementActions,
            availableItems = availableItems,
            currentTab = currentNav.value,
            storage = storage,
            layoutInput = settlementLayout.toContext(),
            terrainInput = terrainSelect.toContext(),
            hexKeyInput = hexKeyInput.toContext(),
            tabs = createTabs<SettlementNav>("change-nav", currentNav)
                .filter {
                    if (it.link == SettlementNav.BONUSES.value) {
                        bonuses.isNotEmpty()
                    } else if (it.link == SettlementNav.STRUCTURES.value) {
                        parsed.constructedStructures.isNotEmpty()
                    } else if (it.link == SettlementNav.NOTES.value) {
                        parsed.notes.isNotEmpty()
                    } else {
                        true
                    }
                }
                .toTypedArray(),
            populationNpcs = current.populationRoster?.npcs ?: emptyArray(),
            trainers = trainersList.toTypedArray(),
            craftingAccess = craftingList.toTypedArray(),
            itemPurchaseLevel = basePurchaseLevel,
        )
    }

    override fun onParsedSubmit(value: InspectSettlementData): Promise<Void> = buildPromise {
        current.lots = value.blocks
        current.type = value.type
        current.secondaryTerritory = value.secondaryTerritory
        current.manualSettlementLevel = value.manualSettlementLevel
        current.waterBorders = value.waterBorders
        current.layoutType = value.layoutType
        current.terrain = value.terrain
        current.hexKey = value.hexKey?.takeIf { it.isNotBlank() }
        undefined
    }

}