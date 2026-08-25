package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.actions.handlers.ClearMealEffectsMessage
import at.posselt.pfrpg2e.actions.handlers.OpenCampingSheetAction
import at.posselt.pfrpg2e.actor.openActor
import at.posselt.pfrpg2e.actor.ownershipOwnersOnly
import at.posselt.pfrpg2e.app.ActorRef
import at.posselt.pfrpg2e.app.DocumentRef
import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.MenuControl
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.app.confirm
import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.OverrideType
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.SelectOption
import at.posselt.pfrpg2e.app.forms.toOption
import at.posselt.pfrpg2e.calculateHexplorationActivities
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.camping.dialogs.AddDowntimeProject
import at.posselt.pfrpg2e.camping.dialogs.CampingSettingsApplication
import at.posselt.pfrpg2e.camping.dialogs.CategoryWeightSettingsApplication
import at.posselt.pfrpg2e.camping.dialogs.ConfirmWatchApplication
import at.posselt.pfrpg2e.camping.dialogs.FavoriteMealsApplication
import at.posselt.pfrpg2e.camping.dialogs.ManageActivitiesApplication
import at.posselt.pfrpg2e.camping.dialogs.ManageRecipesApplication
import at.posselt.pfrpg2e.camping.dialogs.RegionConfig
import at.posselt.pfrpg2e.camping.dialogs.pickSpecialRecipe
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.fromCamelCase
import at.posselt.pfrpg2e.resting.EIGHT_HOURS_SECONDS
import at.posselt.pfrpg2e.resting.getTotalRestDuration
import at.posselt.pfrpg2e.resting.rest
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.takeIfInstance
import at.posselt.pfrpg2e.toCamelCase
import at.posselt.pfrpg2e.utils.MacroData
import at.posselt.pfrpg2e.utils.SheetType
import at.posselt.pfrpg2e.utils.asSequence
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.escapeHtml
import at.posselt.pfrpg2e.utils.formatSeconds
import at.posselt.pfrpg2e.utils.fromDateInputString
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.getPF2EWorldTime
import at.posselt.pfrpg2e.utils.isDay
import at.posselt.pfrpg2e.utils.launch
import at.posselt.pfrpg2e.utils.openItem
import at.posselt.pfrpg2e.utils.openJournal
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.typeSafeUpdate
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.toDateInputString
import at.posselt.pfrpg2e.utils.toMap
import at.posselt.pfrpg2e.utils.toMutableRecord
import js.objects.recordOf
import com.foundryvtt.core.Game
import com.foundryvtt.core.applications.api.ApplicationRenderOptions
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.documents.onCreateItem
import com.foundryvtt.core.documents.onDeleteItem
import com.foundryvtt.core.documents.onUpdateItem
import com.foundryvtt.core.game
import com.foundryvtt.core.helpers.onUpdateWorldTime
import com.foundryvtt.core.ui
import com.foundryvtt.core.utils.fromUuid
import com.foundryvtt.pf2e.actor.PF2EActor
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2ECreature
import com.foundryvtt.pf2e.item.itemFromUuid
import js.array.component1
import js.array.component2
import js.core.Void
import js.objects.Object
import js.objects.ReadonlyRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalTime
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise
import kotlin.math.max
import com.foundryvtt.kingmaker.kingmaker
import com.foundryvtt.kingmaker.KingmakerHex
import at.posselt.pfrpg2e.data.hex.HexContent
import at.posselt.pfrpg2e.data.hex.HexContentType
import at.posselt.pfrpg2e.data.hex.HexContentVisibility
import at.posselt.pfrpg2e.data.regions.Terrain
import at.posselt.pfrpg2e.companion.formatHexKeyLabel
import at.posselt.pfrpg2e.kingdom.getAllSettlements
import kotlin.math.roundToInt
import at.posselt.pfrpg2e.camping.routing.FoundryTravelProvider
import at.posselt.pfrpg2e.camping.routing.TravelRouter
import at.posselt.pfrpg2e.camping.routing.TravelPlan as RoutingTravelPlan
import at.posselt.pfrpg2e.data.regions.getSeasonForMonth
import at.posselt.pfrpg2e.camping.dialogs.RegionSetting
import at.posselt.pfrpg2e.settings.Pfrpg2eKingdomCampingWeatherSettings
import at.posselt.pfrpg2e.utils.getCurrentMonth
import at.posselt.pfrpg2e.weather.getCurrentWeatherType
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors


@JsPlainObject
external interface BaseActorContext {
    val name: String
    val uuid: String
    val image: String?
}

@Suppress("unused")
@JsPlainObject
external interface CampingSheetActor : BaseActorContext {
    val choseActivity: Boolean
    val degreeOfSuccess: FormElementContext?
    val chosenMealImg: String?
    val chosenMeal: String?
    val downtimeHoursRemaining: Int?
    val downtimeHoursMax: Int?
    val downtimeBudgetFull: Boolean?

    // Pre-rendered HTML (escaped) listing the companion activities this actor knows,
    // shown as a Foundry tooltip on the avatar. Null when nothing has been learned.
    val learnedActivities: String?

    // Signed Perception modifier ("+7") shown on watch-slot chips; null outside the
    // watch section or when the actor has no Perception statistic (loot, vehicles).
    val perceptionLabel: String?

    // Consecutive nights this actor has gone unfed. Null when zero, so the template's
    // {{#if hungryDays}} hides the badge entirely for anyone who has been eating.
    val hungryDays: Int?

    // True once the count is past this actor's Constitution-derived endurance -- i.e. the GM has
    // been offered a condition for them. Drives the alarm colour.
    val hungryCritical: Boolean?
}

@Suppress("unused")
@JsPlainObject
external interface CampingSheetActivity {
    val id: String
    val journalUuid: String?
    val actor: CampingSheetActor?
    val name: String
    val hidden: Boolean
    val requiresCheck: Boolean
    val secret: Boolean
    val skills: FormElementContext?
    // Only populated for the "Learn from a Companion" activity: a dropdown of
    // companion activities (whose companion is present) the player can learn.
    val learnTarget: FormElementContext?
    val disabled: Boolean
    val disabledReason: String?
    // No-check activities only: times performed this session and the hours that cost.
    val repetitions: Int
    val repetitionHours: Int
    // Locks the + (perform again) button when the actor's downtime budget is exhausted;
    // the rest of the tile stays interactive so repetitions can still be refunded.
    val repeatDisabled: Boolean
}

fun CampingSheetActivity.isPrepareCampsite() = id == "prepare-campsite"

@Suppress("unused")
@JsPlainObject
external interface WatchSlotContext {
    val index: Int
    val actors: Array<CampingSheetActor>

    // "HH:MM – HH:MM" offsets into the rest this slot covers, using the same night
    // division the rest flow uses to map an encounter to its on-duty slot; null when
    // the rest duration cannot be computed.
    val hourRange: String?
}

@Suppress("unused")
@JsPlainObject
external interface NightModes {
    val retract2: Boolean
    val retract1: Boolean
    val retractHex: Boolean
    val time: Boolean
    val advanceHex: Boolean
    val advance1: Boolean
    val advance2: Boolean
    val rest: Boolean
    val travelMode: Boolean
    val forcedMarch: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface RecipeActorContext : BaseActorContext {
    val chosenMeal: String
    val favoriteMeal: String?
}

@Suppress("unused")
@JsPlainObject
external interface RecipeContext {
    val name: String
    val targetRecipe: String
    val cost: FoodCost?
    val uuid: String?
    val icon: String
    val requiresCheck: Boolean
    val hidden: Boolean
    val rations: Boolean
    val consumeRationsEnabled: Boolean
    val actors: Array<RecipeActorContext>
    val skills: FormElementContext?
    val degreeOfSuccess: FormElementContext?
    val level: Int?
    val rarity: String?
    val purchaseCost: String?
    val requirements: String?
}

@Suppress("unused")
@JsPlainObject
external interface TravelRouteUiContext {
    val totalCost: Double
    val totalDistance: Int
    val estimatedDuration: String
    val path: Array<String>
    val modifiers: Array<String>
    /** Non-null when the route's day count exceeds the party's durable days of food. */
    val foodWarning: String?
    /** e.g. "25 ft" — the party travel Speed the pace multiplier came from. */
    val travelSpeedLabel: String
    /** One-line hover summary. Escaped into a data-tooltip attribute, so it stays plain text. */
    val travelSpeedTooltip: String
    /** Full derivation, rendered as a list so each line is escaped independently. */
    val travelSpeedDetails: Array<String>
}

@Suppress("unused")
@JsPlainObject
external interface CampingSheetContext : ValidatedHandlebarsContext {
    /** PC downtime projects (plan phase 4); rows for everyone, controls are GM surfaces. */
    val downtimeProjects: DowntimeSectionContext?
    var actors: Array<CampingSheetActor>
    var prepareCamp: CampingSheetActivity?
    var activities: Array<CampingSheetActivity>
    var isDay: Boolean
    var isGM: Boolean
    var time: String
    var terrain: String
    var pxTimeOffset: Int
    var night: NightModes
    var hexplorationActivityDuration: String
    var hexplorationActivitiesAvailable: Int

    /** Travel-journal rows, NEWEST FIRST for display. Empty when nothing has been recorded. */
    var travelJournalRows: Array<TravelJournalRow>

    /** Selectable hexploration activity types for the +1 control. */
    var hexplorationActivityOptions: Array<TravelJournalOption>

    /** Number of journal entries, shown in the collapsed summary so the log advertises its content. */
    var travelJournalCount: Int
    var hexplorationActivitiesMax: String
    var adventuringFor: String
    var travelingFor: String
    var restDuration: String
    var restDurationLeft: String?
    var encounterDc: Int
    var region: FormElementContext
    var section: String
    var prepareCampSection: Boolean
    var campingActivitiesSection: Boolean
    var eatingSection: Boolean
    var needsCookAssignment: Boolean
    var setWatchesSection: Boolean
    var watchSlots: Array<WatchSlotContext>

    /** Non-blocking watch-assignment warnings (unassigned campers, empty/duplicate slots); null when clean. */
    var watchWarnings: Array<String>?
    var numberOfWatches: FormElementContext
    var travelMode: FormElementContext
    var forcedMarch: FormElementContext
    var forcedMarchDays: Int
    var forcedMarchMaxDays: Int

    /** True while the party has marched past its endurance; drives the warning banner. */
    var forcedMarchOverLimit: Boolean
    var recipes: Array<RecipeContext>
    var totalFoodCost: FoodCost
    var availableFood: FoodCost
    /** Durable days-of-food forecast label ("3", or "∞" when nobody is eating). */
    var foodDaysDisplay: String
    /** Whether tonight's meal is covered by current rations + provisions. */
    var foodTonightCovered: Boolean
    var canRollEncounter: Boolean
    var sheetBackground: String
    var travelStartHexSelect: FormElementContext?
    var travelEndHexSelect: FormElementContext?
    var travelRoute: TravelRouteUiContext?
    var travelMoveToken: Boolean
    var travelPathError: String?
}

@JsPlainObject
external interface CampingSheetActivitiesFormData {
    val degreeOfSuccess: ReadonlyRecord<String, String?>?
    val selectedSkill: ReadonlyRecord<String, String?>?
    val learnTarget: ReadonlyRecord<String, String?>?
}

@JsPlainObject
external interface RecipeFormData {
    val selectedSkill: ReadonlyRecord<String, String?>?
    val degreeOfSuccess: ReadonlyRecord<String, String?>?
}

@JsPlainObject
external interface CampingSheetFormData {
    val region: String
    val activities: CampingSheetActivitiesFormData
    val recipes: RecipeFormData?
    val travelModeActive: Boolean
    val forcedMarchActive: Boolean
    val numberOfWatches: Int?
    val travelStartHex: String?
    val travelEndHex: String?
    val travelMoveToken: Boolean?
}

private fun isNightMode(
    now: LocalTime,
    visibleAfter: String,
    visibleBefore: String,
): Boolean {
    val start = LocalTime.fromDateInputString(visibleAfter)
    val end = LocalTime.fromDateInputString(visibleBefore)
    return if (start > end) {
        !((now < end) || (now > start))
    } else {
        !((start < now) && (now < end))
    }
}

private fun calculateNightModes(time: LocalTime): NightModes {
    return NightModes(
        travelMode = isNightMode(time, "16:00", "05:30"),
        forcedMarch = isNightMode(time, "13:30", "04:30"),
        retract2 = isNightMode(time, "10:00", "23:00"),
        retract1 = isNightMode(time, "09:00", "22:00"),
        retractHex = isNightMode(time, "08:00", "21:00"),
        time = isNightMode(time, "06:00", "19:00"),
        advanceHex = isNightMode(time, "04:00", "17:00"),
        advance1 = isNightMode(time, "03:00", "16:00"),
        advance2 = isNightMode(time, "02:00", "15:00"),
        rest = isNightMode(time, "19:00", "08:00"),
    )
}

private const val windowWidth = 970


@JsName("CampingSheet")
class CampingSheet(
    private val game: Game,
    private val actor: CampingActor,
    private val dispatcher: ActionDispatcher,
) : FormApp<CampingSheetContext, CampingSheetFormData>(
    title = t("applications.camping"),
    template = "applications/camping/camping-sheet.hbs",
    id = "kmCamping-${actor.uuid}",
    width = windowWidth,
    dataModel = CampingSheetDataModel::class.js,
    filterBlanks = false,
    classes = setOf("km-camping-sheet"),
    controls = arrayOf(
        MenuControl(label = t("camping.showPlayers"), action = "show-players", gmOnly = true),
        MenuControl(label = t("camping.resetActivities"), action = "reset-activities", gmOnly = true),
        MenuControl(label = t("camping.resetMeals"), action = "reset-meals", gmOnly = true),
        MenuControl(label = t("camping.favoriteMeals"), action = "favorite-meals", gmOnly = false),
        MenuControl(label = t("camping.activities"), action = "configure-activities", gmOnly = true),
        MenuControl(label = t("camping.recipes"), action = "configure-recipes", gmOnly = true),
        MenuControl(label = t("camping.regions"), action = "configure-regions", gmOnly = true),
        MenuControl(label = t("camping.encounterCurator"), action = "open-encounter-curator", gmOnly = true),
        MenuControl(label = t("applications.settings"), action = "settings", gmOnly = true),
        MenuControl(label = t("applications.quickstart"), action = "quickstart", gmOnly = true),
        MenuControl(label = t("applications.help"), action = "help"),
    ),
    scrollable = setOf(".km-camping-activities-wrapper", ".km-camping-actors"),
    syncedDocument = actor,
    debug = true,
) {
    init {
        onDocumentRefDragstart(".km-camping-actor")
        onDocumentRefDragstart(".km-recipe-actor")
        onDocumentRefDragstart(".km-camping-watch-assignee")
        onDocumentRefDrop(".km-camping-add-actor") { _, documentRef ->
            if (documentRef is ActorRef) {
                buildPromise {
                    val actorId = fromUuid(documentRef.uuid).await()?.id!!
                    addActor(documentRef.uuid, actorId)
                }
            }
        }
        onDocumentRefDrop(
            ".km-camping-actor",
            { it.type == "Item" }
        ) { event, documentRef ->
            buildPromise {
                val target = event.target as HTMLElement
                val tile = target.closest(".km-camping-actor") as HTMLElement?
                val actor = tile?.dataset?.get("uuid")
                    ?.let { fromUuidTypeSafe<PF2EActor>(it) }
                if (actor != null) {
                    addItemToActor(documentRef, actor)
                }
            }
        }

        onDocumentRefDrop(
            ".km-camping-activity",
            { it.dragstartSelector == ".km-camping-actor" || it.type == "Item" }
        ) { event, documentRef ->
            buildPromise {
                val target = event.target as HTMLElement
                val tile = target.closest(".km-camping-activity") as HTMLElement?
                val actor = tile?.dataset?.get("actorUuid")
                    ?.let { fromUuidTypeSafe<PF2EActor>(it) }
                val activityId = tile?.dataset?.get("activityId")
                if (documentRef is ActorRef && activityId != null) {
                    assignActivityTo(documentRef.uuid, activityId)
                } else if (actor != null && activityId != null) {
                    addItemToActor(documentRef, actor)
                }
            }
        }
        onDocumentRefDrop(
            ".km-camping-recipe",
            { it.dragstartSelector == ".km-camping-actor" || it.dragstartSelector == ".km-recipe-actor" }
        ) { event, documentRef ->
            buildPromise {
                val target = event.target as HTMLElement
                val tile = target.closest(".km-camping-recipe") as HTMLElement?
                val recipeId = tile?.dataset?.get("recipeId")
                if (documentRef is ActorRef && recipeId != null) {
                    val actorId = fromUuid(documentRef.uuid).await()?.id!!
                    assignRecipeTo(documentRef.uuid, actorId, recipeId = recipeId)
                }
            }
        }
        onDocumentRefDrop(
            ".km-camping-watch-slot",
            { it.dragstartSelector == ".km-camping-actor" }
        ) { event, documentRef ->
            buildPromise {
                val target = event.target as HTMLElement
                val slot = target.closest(".km-camping-watch-slot") as HTMLElement?
                val slotIndex = slot?.dataset?.get("slotIndex")?.toIntOrNull()
                if (documentRef is ActorRef && slotIndex != null) {
                    assignWatchSlot(documentRef.uuid, slotIndex)
                }
            }
        }
        onDocumentRefDrop(
            ".km-camping-watch-slot",
            { it.dragstartSelector == ".km-camping-watch-assignee" }
        ) { event, documentRef ->
            buildPromise {
                val target = event.target as HTMLElement
                val toSlot = target.closest(".km-camping-watch-slot") as HTMLElement?
                val toIndex = toSlot?.dataset?.get("slotIndex")?.toIntOrNull()
                val fromSlot = documentRef as? ActorRef
                if (toIndex != null && fromSlot != null) {
                    assignWatchSlot(fromSlot.uuid, toIndex)
                }
            }
        }
        appHook.onUpdateWorldTime { _, _, _, _ -> render() }
        appHook.onCreateItem { _, _, _ -> render() }
        appHook.onDeleteItem { _, _, _ -> render() }
        appHook.onUpdateItem { _, _, _, _ -> render() }
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "configure-regions" -> RegionConfig(actor).launch()
            "configure-recipes" -> ManageRecipesApplication(game, actor).launch()
            "configure-activities" -> ManageActivitiesApplication(game, actor).launch()
            "reset-activities" -> buildPromise {
                if (confirm(t("camping.confirmActivityReset"))) {
                    resetActivities()
                }
            }

            "reset-meals" -> buildPromise {
                if (confirm(t("camping.confirmMealReset"))) {
                    resetMeals()
                }
            }

            "settings" -> CampingSettingsApplication(game, actor).launch()
            "open-encounter-curator" -> CategoryWeightSettingsApplication(game, actor).launch()
            "rest" -> buildPromise {
                beginRest(actor, dispatcher)
            }

            "roll-recipe-check" -> buildPromise {
                target.closest(".km-camping-recipe")
                    ?.takeIfInstance<HTMLElement>()
                    ?.dataset["recipeId"]
                    ?.let { rollRecipeCheck(it) }
            }

            "consume-rations" -> buildPromise {
                consumeRations()
            }

            "roll-camping-check" -> buildPromise {
                target.closest(".km-camping-activity")
                    ?.takeIfInstance<HTMLElement>()
                    ?.let { tile ->
                        tile.dataset["activityId"]?.let { activity ->
                            tile.dataset["actorUuid"]?.let { actorUuid ->
                                rollCheck(activity, actorUuid)
                            }
                        }
                    }
            }

            "favorite-meals" -> buildPromise { FavoriteMealsApplication(game, actor).launch() }
            "next-section" -> buildPromise { nextSection() }
            "previous-section" -> buildPromise { previousSection() }
            "check-encounter" -> buildPromise { rollEncounter(includeFlatCheck = true) }
            "roll-encounter" -> buildPromise { rollEncounter(includeFlatCheck = false) }
            "advance-hour" -> buildPromise {
                advanceHours(target)
            }

            "advance-hexploration" -> buildPromise {
                advanceHexplorationActivities(target)
            }

            "add-downtime-project" -> buildPromise {
                if (!game.user.isGM) return@buildPromise
                val kingdomActor = game.getKingdomActors().firstOrNull() ?: return@buildPromise
                val kingdom = kingdomActor.getKingdom() ?: return@buildPromise
                val pcs = actor.getCamping()?.getActorsInCamp()
                    ?.filterIsInstance<PF2ECharacter>()
                    ?.map { it.uuid to (it.name ?: "?") }
                    ?: emptyList()
                val settlements = kingdom.getAllSettlements(game).allSettlements
                AddDowntimeProject(
                    pcs = pcs,
                    settlements = settlements,
                    afterSubmit = { raw ->
                        kingdomActor.getKingdom()?.let { current ->
                            current.downtimeProjects = (current.downtimeProjects ?: emptyArray()) + raw
                            kingdomActor.setKingdom(current)
                        }
                        render()
                    },
                ).launch()
            }

            "travel-route" -> buildPromise {
                travelPlannedRoute(moveToken = actor.getCamping()?.travelMoveToken == true)
            }

            "clear-actor" -> {
                buildPromise {
                    target.dataset["uuid"]
                        ?.let { fromUuid(it).await() }
                        ?.let { actor.deleteCampingActor(it.uuid, it.id!!) {} }
                }
            }

            "clear-activity" -> {
                buildPromise {
                    target.dataset["id"]?.let { clearActivity(it) }
                }
            }

            "repeat-activity" -> {
                buildPromise {
                    target.dataset["id"]?.let { repeatActivity(it) }
                }
            }

            "remove-activity-repetition" -> {
                buildPromise {
                    target.dataset["id"]?.let { removeActivityRepetition(it) }
                }
            }

            "clear-watch-slot" -> {
                buildPromise {
                    val index = target.dataset["slotIndex"]?.toIntOrNull()
                    val uuid = target.dataset["uuid"]
                    if (index != null && uuid != null) {
                        clearWatchSlot(index, uuid)
                    }
                }
            }

            "suggest-watch-order" -> {
                buildPromise {
                    suggestWatchOrderAction()
                }
            }

            "show-players" -> buildPromise {
                dispatcher.dispatch(
                    ActionMessage(
                        action = "openCampingSheet",
                        data = OpenCampingSheetAction(actorUuid = actor.uuid)
                    )
                )
            }

            "open-journal" -> {
                event.preventDefault()
                event.stopPropagation()
                buildPromise {
                    target.dataset["uuid"]?.let { openJournal(it) }
                }
            }

            "open-item" -> {
                event.preventDefault()
                event.stopPropagation()
                buildPromise {
                    target.dataset["uuid"]?.let { openItem(it) }
                }
            }

            "quickstart" -> buildPromise {
                openJournal("Compendium.pf2e-kingmaker-tools.kingmaker-tools-journals.JournalEntry.kd8cT1Uv9hZOrpgS")
            }


            "help" -> buildPromise {
                openJournal("Compendium.pf2e-kingmaker-tools.kingmaker-tools-journals.JournalEntry.iAQCUYEAq4Dy8uCY.JournalEntryPage.7z4cDr3FMuSy22t1")
            }

            "open-actor" -> {
                event.preventDefault()
                event.stopPropagation()
                buildPromise {
                    target.dataset["uuid"]?.let { openActor(it) }
                }
            }

            "increase-encounter-dc" -> buildPromise {
                target.dataset["value"]?.toInt()?.let { changeEncounterDcModifier(it) }
            }

            "reset-encounter-dc" -> buildPromise {
                changeEncounterDcModifier(null)
            }

            "reset-adventuring-for" -> buildPromise {
                resetAdventuringTimeTracker()
            }
        }
    }

    private suspend fun consumeRations() {
        // the following lines should all be non-null if everything went right
        val camping = actor.getCamping()
        checkNotNull(camping) { "Could not find camping data on actor ${actor.uuid}" }
        val parsed = camping.findCookingChoices(
            charactersInCampByUuid = camping.getActorsInCamp()
                .associateBy { it.uuid },
            recipesById = camping.getAllRecipes()
                .associateBy { it.id },
        )
        val rations = parsed.meals
            .filterIsInstance<MealChoice.Rations>()
            .map { it.cookingCost }
            .sum()
        reduceFoodBy(
            actors = camping.getActorsCarryingFood(actor),
            foodAmount = rations,
            foodItems = getCompendiumFoodItems(),
        )
    }

    /**
     * House rule (t_24158c4b): when the "auto-succeed in claimed hexes" toggle is on and the party's
     * current hex is claimed, Prepare Campsite and Cook Meal skip the roll and take the plain success
     * outcome (degree-dependent crit extras use the plain-success row). Ingredient costs are still
     * paid downstream — only the ROLL is waived. Returns [DegreeOfSuccess.SUCCESS] (and posts the
     * explanatory chat line) when it applies, or null to fall through to the normal roll.
     */
    private suspend fun autoSuccessInOwnLandsResult(
        camping: CampingData,
        activityId: String,
    ): DegreeOfSuccess? {
        if (camping.autoSucceedInClaimedHexes != true) return null
        if (!autoSucceedInClaimedHexes(true, isPartyHexClaimed(game, actor, camping), activityId)) return null
        postChatMessage(t("camping.autoSuccessInOwnLands"), isHtml = true)
        return DegreeOfSuccess.SUCCESS
    }

    /**
     * Foraging-yield modifier for Hunt & Gather derived from the party's surroundings — the current
     * region's terrain, the current season and the current weather. Neutral (no change) when the
     * "vary foraging by terrain/season/weather" toggle is off, restoring flat RAW yields.
     */
    private fun resolveForagingModifier(region: RegionSetting): ForagingModifier {
        if (!Pfrpg2eKingdomCampingWeatherSettings.getEnableForagingModifiers()) return ForagingModifier.NEUTRAL
        return foragingYieldModifier(
            terrain = fromCamelCase<Terrain>(region.terrain),
            season = getSeasonForMonth(game.getCurrentMonth().ordinal),
            weather = game.getCurrentWeatherType(),
        )
    }

    private suspend fun rollRecipeCheck(recipeId: String) {
        // the following lines should all be non-null if everything went right
        val camping = actor.getCamping()
        checkNotNull(camping) { "Could not find camping data on actor ${actor.uuid}" }
        val region = camping.findCurrentRegion()
        checkNotNull(region) { "Could not determine current region" }
        val activityData = camping.groupActivities().find { it.isCookMeal() }
        checkNotNull(activityData) { "Could not find Cook Meal activity" }
        val parsed = camping.findCookingChoices(
            charactersInCampByUuid = camping.getActorsInCamp()
                .filterIsInstance<PF2ECharacter>()
                .associateBy { it.uuid },
            recipesById = camping.getAllRecipes()
                .associateBy { it.id },
        )
        val cook = parsed.cook
        checkNotNull(cook) { "Trying to cook a meal without a selected cook" }
        val mealToCook = parsed.results.find { it.recipe.id == recipeId }
        checkNotNull(mealToCook) { "Could not find meal with id $recipeId" }

        val result = autoSuccessInOwnLandsResult(camping, cookMealId)
            ?: cook.campingActivityCheck(
                data = CampingCheckData(
                    region = region,
                    activityData = activityData,
                    skill = ParsedCampingSkill(
                        attribute = mealToCook.selectedSkill,
                        dcType = DcType.STATIC,
                        dc = mealToCook.dc
                    )
                ),
                overrideDc = mealToCook.dc,
                weather = game.currentWeatherModifiers(camping),
            )
        val existing = camping.cooking.results[recipeId]
        if (existing == null) {
            camping.cooking.results[recipeId] = CookingResult(
                skill = mealToCook.selectedSkill.value,
                result = result?.toCamelCase(),
            )
        } else {
            existing.result = result?.toCamelCase()
        }
        // Notable meals only. Every night has a meal; journaling all of them would bury the trail
        // in noise, so only the criticals -- the ones a table actually remembers -- get a line.
        if (result == DegreeOfSuccess.CRITICAL_SUCCESS || result == DegreeOfSuccess.CRITICAL_FAILURE) {
            camping.appendTravelEntry(
                mealEntry(
                    game = game,
                    recipeName = mealToCook.recipe.name,
                    note = t(result),
                )
            )
        }
        actor.setCamping(camping)
    }

    private suspend fun rollCheck(activityId: String, actorUuid: String) {
        val checkActor = getCampingActivityCreatureByUuid(actorUuid)
        checkNotNull(checkActor) { "Could not find camping actor with uuid $actorUuid" }

        val camping = actor.getCamping()
        checkNotNull(camping) { "Could not find camping data on actor ${actor.uuid}" }

        val campingCheckData = checkActor.getCampingCheckData(camping, activityId)
        checkNotNull(campingCheckData) { "Could not resolve skill or region" }

        val activity = campingCheckData.activityData.data

        // preparing check removes all meal effects; note that this is prone to races
        // when prepare camp would receive meal bonuses which technically shouldn't happen
        if (activity.isPrepareCampsite()) {
            val message = ActionMessage(
                action = "clearMealEffects",
                data = ClearMealEffectsMessage(campingActorUuid = actor.uuid)
            )
            dispatcher.dispatch(message)
            postChatMessage(t("camping.preparingCampsite"))
            val existingCampingResult = camping.worldSceneId?.let { findExistingCampsiteResult(game, it, actor) }
            if (existingCampingResult != null
                && confirm(
                    t(
                        "camping.reuseCampsiteConfirmation",
                        recordOf("degreeOfSuccess" to t(existingCampingResult))
                    )
                )
            ) {
                camping.campingActivities[activityId]?.result = existingCampingResult.toCamelCase()
                postPassTimeMessage(t("camping.reuseCampsite"), 1)
                actor.setCamping(camping)
                return
            }
        }

        // if it's a recipe we need to know the dc
        val recipe = if (activity.isDiscoverSpecialMeal()) askRecipe(camping) else null
        (autoSuccessInOwnLandsResult(camping, activityId)
            ?: checkActor.campingActivityCheck(
                data = campingCheckData,
                overrideDc = recipe?.cookingLoreDC,
                weather = game.currentWeatherModifiers(camping),
            ))?.let { result ->
            camping.campingActivities[activityId]?.result = result.toCamelCase()
            if (!activity.isPrepareCampsite()) {
                camping.spendDowntimeHours(actorUuid, CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
            }
            actor.setCamping(camping)

            if (activity.isHuntAndGather()) {
                postHuntAndGather(
                    actor = checkActor,
                    degreeOfSuccess = result,
                    zoneDc = campingCheckData.region.zoneDc,
                    regionLevel = campingCheckData.region.level,
                    campingActor = actor,
                    foraging = resolveForagingModifier(campingCheckData.region),
                )
            } else if (activity.isDiscoverSpecialMeal() && recipe != null) {
                postDiscoverSpecialMeal(
                    actorUuid = checkActor.uuid,
                    recipe = recipe,
                    degreeOfSuccess = result,
                    campingActorUuid = actor.uuid,
                )
            } else if (activity.isPrepareCampsite()) {
                postPassTimeMessage(t("camping.prepareNewCampsite"), 2)
            }
        }
    }

    private suspend fun askRecipe(
        camping: CampingData
    ): RecipeData? =
        try {
            pickSpecialRecipe(camping = camping, partyActor = actor)
        } catch (_: Exception) {
            ui.notifications.error(t("camping.noRecipeChosen"))
            null
        }


    private suspend fun resetMeals() {
        actor.getCamping()?.let { camping ->
            Object.values(camping.cooking.actorMeals).forEach { it.chosenMeal = "nothing" }
            actor.setCamping(camping)
        }
    }


    private suspend fun resetActivities() {
        actor.getCamping()?.let { camping ->
            val ids = Object.keys(camping.campingActivities)
                .filter { it != prepareCampsiteId }
                .toSet()
            actor.deleteCampingActivities(ids) {}
            actor.getCamping()?.let { fresh ->
                fresh.resetDowntimeHours()
                actor.setCamping(fresh)
            }
        }
    }

    private suspend fun previousSection() {
        actor.getCamping()?.let { camping ->
            camping.section = when (fromCamelCase<CampingSheetSection>(camping.section)) {
                CampingSheetSection.SET_WATCHES -> CampingSheetSection.EATING
                CampingSheetSection.EATING -> CampingSheetSection.CAMPING_ACTIVITIES
                else -> CampingSheetSection.PREPARE_CAMPSITE
            }.toCamelCase()
            actor.setCamping(camping)
        }
    }

    private suspend fun nextSection() {
        actor.getCamping()?.let { camping ->
            camping.section = when (fromCamelCase<CampingSheetSection>(camping.section)) {
                CampingSheetSection.PREPARE_CAMPSITE -> CampingSheetSection.CAMPING_ACTIVITIES
                CampingSheetSection.CAMPING_ACTIVITIES -> CampingSheetSection.EATING
                CampingSheetSection.EATING -> CampingSheetSection.SET_WATCHES
                else -> CampingSheetSection.SET_WATCHES
            }.toCamelCase()
            if (camping.section == "setWatches") {
                ensureWatchSlots(camping)
            }
            actor.setCamping(camping)
        }
    }

    private suspend fun rollEncounter(includeFlatCheck: Boolean) {
        rollRandomEncounter(game, actor, includeFlatCheck)
    }

    private suspend fun assignRecipeTo(actorUuid: String, actorId: String, recipeId: String) {
        val isNotACharacter = getCampingActivityActorByUuid(actorUuid) == null
        val isNotARation = recipeId != "rationsOrSubsistence" && recipeId != "nothing"
        if (isNotARation && isNotACharacter) {
            ui.notifications.error(t("camping.onlyCharactersConsumeMeals"))
            return
        }
        actor.getCamping()?.let { camping ->
            val existingMeal = camping.cooking.actorMeals[actorId]
            if (existingMeal == null) {
                camping.cooking.actorMeals[actorId] = ActorMeal(
                    actorUuid = actorUuid,
                    chosenMeal = recipeId,
                )
            } else {
                existingMeal.chosenMeal = recipeId
            }
            actor.setCamping(camping)
        }
    }

    private suspend fun assignWatchSlot(actorUuid: String, slotIndex: Int) {
        actor.getCamping()?.let { camping ->
            ensureWatchSlots(camping)
            if (slotIndex < 0 || slotIndex >= camping.watchSlots.size) return
            // An actor can only be on one watch, so remove it from every slot first,
            // then append it to the target slot (slots can hold multiple actors).
            camping.watchSlots = camping.watchSlots
                .mapIndexed { index, slot ->
                    val without = slot.filter { it != actorUuid }
                    if (index == slotIndex) {
                        (without + actorUuid).toTypedArray()
                    } else {
                        without.toTypedArray()
                    }
                }
                .toTypedArray()
            actor.setCamping(camping)
        }
    }

    private suspend fun clearWatchSlot(slotIndex: Int, actorUuid: String) {
        actor.getCamping()?.let { camping ->
            ensureWatchSlots(camping)
            if (slotIndex < 0 || slotIndex >= camping.watchSlots.size) return
            camping.watchSlots[slotIndex] = camping.watchSlots[slotIndex]
                .filter { it != actorUuid }
                .toTypedArray()
            actor.setCamping(camping)
        }
    }

    /**
     * Replace the watch assignments with the suggested order — an even spread of best Perception
     * across the current number of slots (see [suggestWatchOrder]) covering every present,
     * non-exempt camper. Advisory: applied only on click, and the drag/drop path is untouched.
     */
    private suspend fun suggestWatchOrderAction() {
        actor.getCamping()?.let { camping ->
            ensureWatchSlots(camping)
            val slotCount = camping.watchSlots.size
            if (slotCount <= 0) return
            val actorsByUuid = getCampingActorsByUuid(camping.actorUuids).associateBy(PF2EActor::uuid)
            val campers = camping.actorUuids
                .filter { !camping.actorUuidsNotKeepingWatch.contains(it) }
                .mapNotNull { uuid ->
                    actorsByUuid[uuid]?.let { act ->
                        WatchCamper(
                            uuid = uuid,
                            perceptionModifier = runCatching { act.perception.mod }.getOrNull() ?: 0,
                        )
                    }
                }
            camping.watchSlots = suggestWatchOrder(campers, slotCount)
                .map { it.toTypedArray() }
                .toTypedArray()
            actor.setCamping(camping)
        }
    }

    /**
     * Resizes [CampingData.watchSlots] to the desired number of watches, preserving existing
     * assignments. When no watches have been configured yet, falls back to
     * [defaultNumberOfWatches]. This is the single place that determines how many watch slots
     * exist; the slot count doubles as the value shown in the "number of watches" dropdown.
     */
    private fun ensureWatchSlots(camping: CampingData, desired: Int? = null) {
        val current = camping.watchSlots
        val target = (desired ?: current.size.takeIf { it > 0 } ?: defaultNumberOfWatches)
            .coerceIn(minNumberOfWatches, maxNumberOfWatches)
        camping.watchSlots = Array(target) { index -> current.getOrNull(index) ?: emptyArray() }
    }

    private suspend fun assignActivityTo(actorUuid: String, activityId: String) {
        actor.getCamping()?.let { camping ->
            val activity = camping.getAllActivities().find { it.id == activityId }
            val activityActor = getCampingActivityCreatureByUuid(actorUuid)
            if (activityActor == null) {
                ui.notifications.error(t("camping.onlyCharactersCanPerformActivities"))
            } else if (activity == null) {
                ui.notifications.error(t("camping.activityNotFound", recordOf("id" to activityId)))
            } else {
                val companionUnavailable = activity.requiredCompanion?.let { companionName ->
                    val unavailableCompanionNames = (game.getKingdomActors().firstOrNull()?.getKingdom()
                        ?.companions ?: emptyArray())
                        .filter { companion ->
                            companion.asDynamic().campAvailable == false
                                || companion.asDynamic().expeditionStatus == "onExpedition"
                        }
                        .map { it.name }
                        .toSet()
                    val regex = Regex("\\b$companionName\\b", RegexOption.IGNORE_CASE)
                    unavailableCompanionNames.any { regex.containsMatchIn(it) }
                } ?: false

                if (!activityActor.satisfiesAnyActivitySkillRequirement(activity, camping.ignoreSkillRequirements)) {
                    ui.notifications.error(
                        t(
                            "camping.actorLacksSkillRequirements",
                            recordOf("activityName" to activity.name)
                        )
                    )
                } else if (activity.requiresACheck() && !activityActor.hasAnyActivitySkill(activity)) {
                    ui.notifications.error(t("camping.actorLacksSkills", recordOf("activityName" to activity.name)))
                } else if (!camping.canActorPerformActivity(activity, actorUuid, activityActor.name, companionUnavailable)) {
                    ui.notifications.error(
                        t(
                            "camping.actorCannotPerformCompanionActivity",
                            recordOf(
                                "actor" to activityActor.name,
                                "activityName" to activity.name,
                                "companion" to (activity.requiredCompanion ?: ""),
                            )
                        )
                    )
                } else {
                    // GMs can always (re)assign actors; players are subject to scheduling rules
                    val schedulingResult = if (game.user.isGM) {
                        CampingActivityScheduler.SchedulingResult.Allowed
                    } else {
                        CampingActivityScheduler.canAssign(
                            activities = camping.campingActivitiesWithId(),
                            activityData = activity,
                            actorUuid = actorUuid,
                        )
                    }
                    when (schedulingResult) {
                        is CampingActivityScheduler.SchedulingResult.Blocked -> {
                            ui.notifications.error(schedulingResult.reason)
                        }
                        is CampingActivityScheduler.SchedulingResult.Allowed -> {
                            val skill = activityActor
                                .findCampingActivitySkills(activity, camping.ignoreSkillRequirements)
                                .filterNot { it.validateOnly }
                                .firstOrNull()
                            val previous = camping.campingActivities[activityId]
                            val previousActorUuid = previous?.actorUuid
                            actor.typedCampingUpdate { current ->
                                campingActivities[activityId] = CampingActivity(
                                    actorUuid = actorUuid,
                                    selectedSkill = skill?.attribute?.value,
                                    // Re-dropping the same actor keeps their repetitions; a new
                                    // actor starts over at one.
                                    repetitions = if (previousActorUuid == actorUuid) previous?.repetitions else 1,
                                )
                                // No-check activities have no roll to charge on, so the drop itself
                                // charges the hours; a reassignment moves the charge to the new actor,
                                // refunding every repetition the previous actor had accumulated.
                                if (!activity.requiresACheck() && previousActorUuid != actorUuid) {
                                    downtimeHoursSpent.set(
                                        moveNoCheckDowntimeCharge(
                                            spent = current.downtimeHoursSpent,
                                            previousActorUuid = previousActorUuid,
                                            newActorUuid = actorUuid,
                                            refundHours = (previous?.repetitionsOrDefault() ?: 0) *
                                                CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun changeEncounterDcModifier(modifier: Int?) {
        actor.getCamping()?.let { camping ->
            if (modifier == null) {
                camping.encounterModifier = 0
            } else {
                val encounterDc = camping.findCurrentRegion()?.encounterDc ?: 0
                val totalDc = encounterDc + camping.encounterModifier + modifier
                val mod = if (totalDc > 20) {
                    20 - encounterDc
                } else if (totalDc < 0) {
                    -encounterDc
                } else {
                    camping.encounterModifier + modifier
                }
                camping.encounterModifier = mod
            }
            actor.setCamping(camping)
        }
    }

    private suspend fun resetAdventuringTimeTracker() {
        actor.getCamping()?.let { camping ->
            camping.resetTimeTracking(game)
            actor.setCamping(camping)
        }
    }

    private suspend fun addActor(uuid: String, id: String) {
        actor.getCamping()?.let { camping ->
            if (uuid !in camping.actorUuids) {
                val campingActor = getCampingActorByUuid(uuid)
                if (campingActor == null) {
                    ui.notifications.error(t("camping.wrongActorAddedToSheet"))
                } else {
                    camping.actorUuids += uuid
                    camping.cooking.actorMeals[id] = ActorMeal(
                        actorUuid = uuid,
                        favoriteMeal = null,
                        chosenMeal = "nothing",
                    )
                    actor.setCamping(camping)
                }
            }
        }
    }

    private suspend fun addItemToActor(documentRef: DocumentRef<*>, actor: PF2EActor) {
        val document = documentRef.getDocument()
        if (allowedDnDItems.any { it.isInstance(document) }) {
            actor.addToInventory(document.toObject()).await()
        } else {
            ui.notifications.error(t("camping.wrongItemAddedToActor"))
        }
    }

    private suspend fun clearActivity(id: String) {
        actor.getCamping()?.let { camping ->
            // No-check activities charged their hours on drop; unassigning refunds every
            // repetition. Rolled activities keep their spent hours (re-roll costs
            // accumulate by design).
            val campingActivity = camping.campingActivities[id]
            val assignedUuid = campingActivity?.actorUuid
            if (assignedUuid != null) {
                val activity = camping.getAllActivities().find { it.id == id }
                if (activity?.requiresACheck() == false) {
                    camping.refundDowntimeHours(
                        assignedUuid,
                        campingActivity.repetitionsOrDefault() * CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY,
                    )
                }
            }
            campingActivity?.actorUuid = null
            campingActivity?.repetitions = null
            actor.setCamping(camping)
        }
    }

    /**
     * Performs an assigned no-check activity one more time, charging another
     * [CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY] of the actor's downtime.
     */
    private suspend fun repeatActivity(id: String) {
        val camping = actor.getCamping() ?: return
        val campingActivity = camping.campingActivities[id] ?: return
        val assignedUuid = campingActivity.actorUuid ?: return
        val activity = camping.getAllActivities().find { it.id == id } ?: return
        if (activity.requiresACheck()) {
            return
        }
        // Players can only repeat while budget remains; the GM may always.
        if (!game.user.isGM && camping.downtimeHoursRemaining(assignedUuid) <= 0) {
            ui.notifications.error(t("camping.downtimeBudgetExhausted"))
            return
        }
        campingActivity.repetitions = campingActivity.repetitionsOrDefault() + 1
        camping.spendDowntimeHours(assignedUuid, CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
        actor.setCamping(camping)
    }

    /**
     * Undoes one repetition of an assigned no-check activity, refunding its hours;
     * removing the last repetition unassigns the actor entirely.
     */
    private suspend fun removeActivityRepetition(id: String) {
        val camping = actor.getCamping() ?: return
        val campingActivity = camping.campingActivities[id] ?: return
        val assignedUuid = campingActivity.actorUuid ?: return
        val activity = camping.getAllActivities().find { it.id == id }
        if (activity?.requiresACheck() != false) {
            return
        }
        camping.refundDowntimeHours(assignedUuid, CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY)
        val remaining = campingActivity.repetitionsOrDefault() - 1
        if (remaining <= 0) {
            campingActivity.actorUuid = null
            campingActivity.repetitions = null
        } else {
            campingActivity.repetitions = remaining
        }
        actor.setCamping(camping)
    }

    private suspend fun advanceHexplorationActivities(target: HTMLElement) {
        val activities = target.dataset["activities"]?.toInt() ?: 0
        val seconds = getHexplorationActivitySeconds() * activities
        // Only SPENDING an activity is a journal line. The -1 button is a correction, not something
        // the party did, so it records nothing -- the same distinction the companion influence
        // buttons draw between an attempt and an undo.
        if (activities > 0) {
            val chosen = (element.querySelector("#km-hexploration-activity") as? HTMLSelectElement)
                ?.value
                ?.takeIf { it.isNotBlank() }
                ?: HEXPLORATION_ACTIVITY_TYPES.first()
            actor.getCamping()?.let { camping ->
                camping.appendTravelEntry(
                    hexplorationActivityEntry(
                        game = game,
                        activityType = chosen,
                        hexKey = getPartyCurrentHexKey(game, actor, camping),
                    )
                )
                actor.setCamping(camping)
            }
        }
        game.time.advance(seconds).await()
    }

    private fun getAvailableHexplorationSeconds(): Int = EIGHT_HOURS_SECONDS

    private fun getHexplorationActivitySeconds(): Int =
        ((getAvailableHexplorationSeconds()) / getHexplorationActivities()).toInt()

    private fun getHexplorationActivities(): Double {
        val camping = actor.getCamping()!!
        val travelSpeed = actor.system.movement.speeds.travel.value
        val override = max(camping.minimumTravelSpeed ?: 0, travelSpeed)
        val forcedMarch = if (camping.forcedMarchActive == true) 1 else 0
        val result = calculateHexplorationActivities(override) + forcedMarch
        val scaled = result / (camping.hexSizeInMiles.toDouble() / 12)
        // Applied after the hex-size scaling, so foul weather costs a flat activity per day rather
        // than a proportion of one. This is the sole common ancestor of the sheet's displayed budget
        // and splitRouteIntoDays, so both see the same number -- and the floor keeps it away from
        // zero, which would divide by zero in getHexplorationActivitySeconds and make the route
        // splitter treat every leg as its own day.
        return applyWeatherToHexplorationActivities(scaled, game.currentWeatherModifiers(camping))
    }

    private fun getHexplorationActivitiesDuration(): String =
        LocalTime.fromSecondOfDay(getHexplorationActivitySeconds()).toDateInputString()

    private fun getHexplorationActivitiesAvailable(camping: CampingData): Int =
        max(
            0,
            (getAvailableHexplorationSeconds() - camping.secondsSpentHexploring) / getHexplorationActivitySeconds()
        )

    private fun getAdventuringFor(camping: CampingData): String {
        return formatSeconds(camping.secondsSpentHexploring)
    }

    private fun getTravelingFor(camping: CampingData): String {
        return formatSeconds(camping.secondsSpentTraveling)
    }

    private suspend fun advanceHours(target: HTMLElement) {
        game.time.advance(3600 * (target.dataset["hours"]?.toInt() ?: 0)).await()
    }

    private suspend fun getRecipeContext(
        parsedCookingChoices: ParsedMeals,
        foodItems: FoodItems,
        total: FoodAmount,
        camping: CampingData,
        section: CampingSheetSection,
    ): Array<RecipeContext> {
        val actorsByChosenMeal = parsedCookingChoices.meals
            .map { meal ->
                RecipeActorContext(
                    name = meal.actor.name,
                    uuid = meal.actor.uuid,
                    image = meal.actor.img,
                    chosenMeal = meal.name,
                    favoriteMeal = meal.favoriteMeal?.name,
                )
            }
            .groupBy { it.chosenMeal }
        val starving = RecipeContext(
            name = t("camping.skipMeal"),
            targetRecipe = "nothing",
            icon = "icons/containers/kitchenware/bowl-clay-brown.webp",
            requiresCheck = false,
            hidden = section != CampingSheetSection.EATING,
            rations = false,
            consumeRationsEnabled = false,
            actors = actorsByChosenMeal["nothing"]?.toTypedArray() ?: emptyArray(),
        )
        val rationActors: Array<RecipeActorContext> =
            actorsByChosenMeal["rationsOrSubsistence"]?.toTypedArray() ?: emptyArray()
        val rations = RecipeContext(
            name = t("camping.rations"),
            targetRecipe = "rationsOrSubsistence",
            icon = "icons/consumables/food/berries-ration-round-red.webp",
            requiresCheck = false,
            cost = buildFoodCost(
                FoodAmount(rations = 1),
                totalAmount = total,
                items = foodItems,
            ),
            rations = true,
            consumeRationsEnabled = rationActors.isNotEmpty(),
            actors = rationActors,
            hidden = section != CampingSheetSection.EATING,
        )
        val cookMealActor = parsedCookingChoices.cook
        val cookingSkillOptions = parsedCookingChoices.skills.map { it.toOption() }
        val knownRecipes = camping.cooking.knownRecipes.toSet()
        val specialRecipes = camping.getAllRecipes()
            // The basic meal is cooked via the Cook Meal activity tile (basic cooking roll),
            // so it is omitted from the special-meal recipe list.
            .filter { it.id != "basic-meal" }
            .sortedBy { it.name }
        // The compendium item is only consulted for a fallback icon, but resolving it
        // serially per recipe dominated the sheet's render time (one document roundtrip
        // per recipe). Only icon-less recipes need it, and those resolve concurrently.
        val fallbackIconsByRecipeId = coroutineScope {
            specialRecipes
                .filter { it.icon == null }
                .map { recipe -> async { recipe.id to itemFromUuid(recipe.uuid)?.img } }
                .awaitAll()
                .toMap()
        }
        return arrayOf(starving, rations) + specialRecipes
            .map { recipe ->
                val cookingCost = buildFoodCost(
                    recipe.cookingCost(),
                    totalAmount = total,
                    items = foodItems
                )
                val result = parsedCookingChoices.results.find { it.recipe.name == recipe.name }
                RecipeContext(
                    name = recipe.name,
                    targetRecipe = recipe.id,
                    cost = cookingCost,
                    uuid = recipe.uuid,
                    icon = recipe.icon ?: fallbackIconsByRecipeId[recipe.id]
                        ?: "icons/consumables/food/shank-meat-bone-glazed-brown.webp",
                    requiresCheck = true,
                    hidden = section != CampingSheetSection.EATING || cookMealActor == null || recipe.id !in knownRecipes,
                    rations = false,
                    consumeRationsEnabled = false,
                    actors = actorsByChosenMeal[recipe.name]?.toTypedArray() ?: emptyArray(),
                    skills = Select(
                        label = t("camping.selectedSkill"),
                        name = "recipes.selectedSkill.${recipe.id}",
                        hideLabel = true,
                        options = cookingSkillOptions,
                        elementClasses = listOf("km-proficiency"),
                        value = result?.selectedSkill?.value,
                    ).toContext(),
                    degreeOfSuccess = Select.fromEnum<DegreeOfSuccess>(
                        hideLabel = true,
                        required = false,
                        name = "recipes.degreeOfSuccess.${recipe.id}",
                        value = result?.degreeOfSuccess,
                        elementClasses = listOf("km-degree-of-success"),
                    ).toContext(),
                    level = recipe.level,
                    rarity = recipe.rarity,
                    purchaseCost = recipe.cost.format(),
                    requirements = recipe.requirements,
                )
            }
            .toTypedArray()
    }

    private fun calculateTotalFoodCost(
        actorMeals: List<MealChoice>,
        availableFood: FoodAmount,
        foodItems: FoodItems,
    ): FoodCost {
        val amount = actorMeals
            .map { it.cookingCost }
            .sum()
        return buildFoodCost(amount, totalAmount = availableFood, items = foodItems)
    }

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions
    ): Promise<CampingSheetContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val time = game.getPF2EWorldTime().time
        val dayPercentage = time.toSecondOfDay().toFloat() / 86400f
        val widthWithoutBorder = windowWidth - 2
        val pxTimeOffset = -((dayPercentage * widthWithoutBorder).toInt() - widthWithoutBorder / 2)
        val camping = actor.getCamping() ?: getDefaultCamping(game)
        // Parallelize independent suspend lookups: actor UUID resolution and compendium
        // food-item fetching are completely independent of each other.
        val (actors, foodItems) = coroutineScope {
            val actorsDeferred = async { getCampingActorsByUuid(camping.actorUuids) }
            val foodDeferred = async { getCompendiumFoodItems() }
            val actors = actorsDeferred.await()
            val food = foodDeferred.await()
            actors to food
        }
        val actorsByUuid = actors.associateBy(PF2EActor::uuid)
        val charactersByUuid: Map<String, PF2EActor> = actorsByUuid
            .mapNotNull {
                val value = it.value
                it.key to value
            }
            .toMap()
        val groupActivities = camping.groupActivities().sortedBy { it.data.id }
        val section = fromCamelCase<CampingSheetSection>(camping.section) ?: CampingSheetSection.PREPARE_CAMPSITE
        val prepareCampSection = section == CampingSheetSection.PREPARE_CAMPSITE
        val campingActivitiesSection = section == CampingSheetSection.CAMPING_ACTIVITIES
        val eatingSection = section == CampingSheetSection.EATING
        val setWatchesSection = section == CampingSheetSection.SET_WATCHES
        if (setWatchesSection) {
            ensureWatchSlots(camping)
        }
        val totalFood = camping.getTotalCarriedFood(actor, foodItems)
        val availableFood = buildFoodCost(totalFood, items = foodItems)
        // Food forecast (read-only, derived): rations are durable, provisions are tonight-only.
        // dailyConsumers = the camp roster (characters + companions present); RAW baseline is one
        // ration per consumer per day (mealCostRations defaults to 1) — the plain-meal fallback.
        val totalProvisions = camping.getTotalProvisions(actor, foodItems)
        val parsedCookingChoices = camping.findCookingChoices(
            charactersInCampByUuid = charactersByUuid,
            recipesById = camping.getAllRecipes().associateBy { it.id },
        )
        // Computed after the meal plan, because the plan is what the party will actually eat: a camp
        // cooking recipes burns ingredients rather than rations, and a forecast counting only
        // rations would promise days the pantry cannot deliver. Costs are summed per planned meal
        // rather than averaged, since each character may eat something different.
        val plannedMeals = parsedCookingChoices.meals
        val foodForecast = computeFoodForecast(
            FoodForecastInput(
                rations = (totalFood.rations - totalProvisions).coerceAtLeast(0),
                provisions = totalProvisions,
                dailyConsumers = actors.size,
                basicIngredients = totalFood.basicIngredients,
                specialIngredients = totalFood.specialIngredients,
                dailyRationsTotal = plannedMeals.sumOf { it.cookingCost.rations }
                    .takeIf { plannedMeals.isNotEmpty() },
                dailyBasicIngredientsTotal = plannedMeals.sumOf { it.cookingCost.basicIngredients }
                    .takeIf { plannedMeals.isNotEmpty() },
                dailySpecialIngredientsTotal = plannedMeals.sumOf { it.cookingCost.specialIngredients }
                    .takeIf { plannedMeals.isNotEmpty() },
            )
        )
        val foodDaysDisplay =
            if (foodForecast.daysOfFood >= Int.MAX_VALUE / 2) "∞" else foodForecast.daysOfFood.toString()
        val recipesContext = getRecipeContext(
            parsedCookingChoices = parsedCookingChoices,
            foodItems = foodItems,
            total = totalFood,
            camping = camping,
            section = section,
        )
        val chosenMealsByActorUuid = recipesContext.asSequence()
            .flatMap { recipe ->
                recipe.actors.asSequence()
                    .filter { it.chosenMeal != "nothing" }
                    .map { it.uuid to recipe }
            }
            .toMap()
        // Companions physically in camp but flagged unavailable (campAvailable == false) gate their
        // required activities even when present (roadmap #7). Null/undefined = available (backward compat).
        // Companions on expedition are also unavailable for camp activities.
        val unavailableCompanionNames = (game.getKingdomActors().firstOrNull()?.getKingdom()
            ?.companions ?: emptyArray())
            .filter { companion ->
                companion.asDynamic().campAvailable == false
                    || companion.asDynamic().expeditionStatus == "onExpedition"
            }
            .map { it.name }
            .toSet()
        val activities = groupActivities.mapIndexed { _, groupedActivity ->
            val (data, result) = groupedActivity
            val actor = result.actorUuid?.let { actorsByUuid[it] }?.unsafeCast<PF2ECreature>()
            val requiresCheck = !data.doesNotRequireACheck()
            val skills = getActivitySkills(
                actor = actor,
                groupedActivity = groupedActivity,
                ignoreSkillRequirements = camping.ignoreSkillRequirements,
            )
            // Companion-learning activities must always be listed (greyed out when the
            // required NPC is absent), so they bypass the manage-activities lock. Every
            // other activity continues to respect lockedActivities as before.
            val hidden = data.isHiddenByLock(camping.lockedActivities.toSet())
                    || (prepareCampSection && !groupedActivity.isPrepareCamp())
                    || (campingActivitiesSection && groupedActivity.isPrepareCamp())
                    || eatingSection
                    || setWatchesSection
                    || camping.alwaysPerformActivityIds.contains(data.id)
            val isCompanionPresent = data.isRequiredCompanionPresent(
                actorNames = actorsByUuid.values.map { it.name }.toSet()
            )
            val anyoneLearned = actorsByUuid.keys.any { camping.hasActorLearnedActivity(it, data.id) }
            val requiredCompanionUnavailable = data.requiredCompanion?.let { companionName ->
                val regex = Regex("\\b$companionName\\b", RegexOption.IGNORE_CASE)
                unavailableCompanionNames.any { regex.containsMatchIn(it) }
            } ?: false
            val companionDisabled = !hidden && data.requiredCompanion != null && if (actor != null) {
                // A specific actor is assigned: only the companion themselves or a character
                // who has learned the activity may perform it — presence alone is not enough.
                !camping.canActorPerformActivity(data, actor.uuid, actor.name, requiredCompanionUnavailable)
            } else {
                // No one assigned yet: the tile is usable if the companion is in camp (and
                // available) or someone in camp has learned it.
                !((isCompanionPresent && !requiredCompanionUnavailable) || anyoneLearned)
            }
            val budgetExhausted = !hidden && actor != null && !data.isPrepareCampsite() && camping.downtimeHoursRemaining(actor.uuid) <= 0
            // Assigned no-check tiles must stay clickable when the budget runs out —
            // .disabled sets pointer-events: none, which would lock the player out of
            // refunding repetitions. Only the tile's + button locks instead.
            val budgetDisabled = budgetExhausted && requiresCheck
            val disabled = companionDisabled || budgetDisabled
            val disabledReason = if (companionDisabled) {
                if (actor != null) {
                    t(
                        "camping.activityRequiresCompanionOrLearned",
                        recordOf("companion" to data.requiredCompanion)
                    )
                } else {
                    t(
                        "camping.activityRequiresCompanion",
                        recordOf("companion" to data.requiredCompanion)
                    )
                }
            } else if (budgetDisabled) {
                t("camping.downtimeBudgetExhausted")
            } else null
            val learnTarget = if (data.isLearnFromCompanion()) {
                getLearnTargetSelect(
                    activityId = data.id,
                    camping = camping,
                    actorUuid = result.actorUuid,
                    presentActorNames = actorsByUuid.values.map { it.name }.toSet(),
                    selected = groupedActivity.result.learnTargetActivityId,
                )
            } else null
            val repetitions = if (!requiresCheck && actor != null) result.repetitionsOrDefault() else 1
            CampingSheetActivity(
                id = data.id,
                secret = data.isSecret && !game.user.isGM,
                journalUuid = data.journalUuid,
                name = data.name,
                hidden = hidden,
                requiresCheck = requiresCheck,
                skills = skills,
                learnTarget = learnTarget,
                disabled = disabled,
                disabledReason = disabledReason,
                repetitions = repetitions,
                repetitionHours = repetitions * CampingActivityScheduler.DOWNTIME_HOURS_PER_ACTIVITY,
                repeatDisabled = budgetExhausted,
                actor = actor?.let { act ->
                    val degree = result.result?.let { fromCamelCase<DegreeOfSuccess>(it) }
                    CampingSheetActor(
                        name = act.name,
                        uuid = act.uuid,
                        image = act.img,
                        choseActivity = true,
                        degreeOfSuccess = Select.fromEnum<DegreeOfSuccess>(
                            hideLabel = true,
                            required = false,
                            name = "activities.degreeOfSuccess.${data.id}",
                            value = degree,
                            elementClasses = listOf("km-degree-of-success"),
                        ).toContext(),
                    )
                },
            )
        }.toTypedArray()
        val recipes = camping.getAllRecipes()
        val fullRestDuration = getTotalRestDuration(
            watchers = actorsByUuid.values.filter { !camping.actorUuidsNotKeepingWatch.contains(it.uuid) },
            recipes = recipes.toList(),
            gunsToClean = camping.gunsToClean,
            increaseActorsKeepingWatch = camping.increaseWatchActorNumber,
            remainingSeconds = camping.watchSecondsRemaining,
            skipWatch = false,
            skipDailyPreparations = false,
        )
        // Watch panel: per-slot hour ranges divide the SAME total the rest flow divides when
        // mapping a random encounter to its on-duty slot, so display and mechanics agree.
        val watchDurationSeconds = fullRestDuration.total.value
        val nonExemptPresentUuids = camping.actorUuids
            .filter { !camping.actorUuidsNotKeepingWatch.contains(it) && actorsByUuid[it] != null }
        val watchValidation = validateWatchAssignments(
            nonExemptUuids = nonExemptPresentUuids,
            slots = camping.watchSlots.map { it.toList() },
        )
        val watchWarnings = if (!setWatchesSection || watchValidation.isClean) {
            null
        } else {
            buildList {
                watchValidation.emptySlotIndices.forEach { idx ->
                    add(t("camping.watchWarningEmptySlot", recordOf("slot" to idx + 1)))
                }
                if (watchValidation.unassignedUuids.isNotEmpty()) {
                    val names = watchValidation.unassignedUuids.mapNotNull { actorsByUuid[it]?.name }
                    add(t("camping.watchWarningUnassigned", recordOf("names" to names.joinToString(", "))))
                }
                if (watchValidation.duplicateUuids.isNotEmpty()) {
                    val names = watchValidation.duplicateUuids.mapNotNull { actorsByUuid[it]?.name }
                    add(t("camping.watchWarningDuplicate", recordOf("names" to names.joinToString(", "))))
                }
            }.toTypedArray()
        }
        val currentRegion = camping.findCurrentRegion()
        val regions = camping.regionSettings.regions
        val isGM = game.user.isGM
        val uncookedMeals = parsedCookingChoices.results
            .filter { it.degreeOfSuccess == null }
            .map { it.recipe.name }
            .toSet()
        val hexplorationActivityDuration = getHexplorationActivitiesDuration()
        val hexplorationActivitiesAvailable = getHexplorationActivitiesAvailable(camping)
        val hexplorationActivitiesMax = "${getHexplorationActivities()}"
        val nightModes = calculateNightModes(time)
        val currentTerrain = currentRegion?.terrain ?: "plains"
        val background = game.settings.pfrpg2eKingdomCampingWeather
            .resolveCampingBackground(currentTerrain, time.isDay())

        val weatherType = try {
            game.settings.pfrpg2eKingdomCampingWeather.getCurrentWeatherType()
        } catch (e: Throwable) {
            "sunny"
        }
        val weatherModifier = when (weatherType.lowercase()) {
            "rainy" -> 1.5
            "snowy" -> 2.0
            "cold" -> 1.5
            else -> 1.0
        }

        val travelSpeed = try {
            actor.system.movement.speeds.travel.value.toDouble()
        } catch (e: Throwable) {
            DEFAULT_PARTY_SPEED_FEET.toDouble()
        }

        // Explain the pace in the UI rather than leaving players to reverse-engineer it: the
        // party Speed is PF2e's minimum across members, and the route planner turns it into a
        // multiplier against a fixed baseline.
        val memberSpeeds = runCatching {
            actor.members.mapNotNull { member ->
                val speed = member.asDynamic().system?.movement?.speeds?.travel?.value as? Int
                speed?.let { MemberSpeed(name = member.name, speedFeet = it) }
            }
        }.getOrDefault(emptyList())
        val speedBreakdown = explainTravelSpeed(
            partySpeedFeet = travelSpeed.toInt(),
            members = memberSpeeds,
        )
        val speedTooltipLines = buildList {
            add(t("camping.travelSpeedHelpParty"))
            if (speedBreakdown.slowest.isEmpty()) {
                add(t("camping.travelSpeedHelpNoMembers"))
            } else {
                add(
                    t(
                        "camping.travelSpeedHelpSlowest",
                        recordOf("names" to speedBreakdown.slowest.joinToString(", ") { it.name }),
                    )
                )
            }
            add(
                t(
                    "camping.travelSpeedHelpPace",
                    recordOf(
                        "activities" to speedBreakdown.hexplorationActivitiesPerDay.toString(),
                        "hours" to LocalTime.fromSecondOfDay(getHexplorationActivitySeconds()).toDateInputString(),
                    ),
                )
            )
            add(t("camping.travelSpeedHelpRouteFormula"))
            add(t("camping.travelSpeedHelpHexploration"))
        }
        // The hover tooltip is a single plain-text line: it goes into a data-tooltip attribute,
        // which Handlebars escapes, and it must stay escaped because member names are actor names
        // and therefore user-controlled. The full derivation renders as a list instead, so every
        // line is escaped on its own rather than smuggled through one attribute.
        val travelSpeedTooltip = t(
            "camping.travelSpeedHelpPace",
            recordOf(
                "activities" to speedBreakdown.hexplorationActivitiesPerDay.toString(),
                "hours" to LocalTime.fromSecondOfDay(getHexplorationActivitySeconds()).toDateInputString(),
            ),
        )
        val travelSpeedDetails = speedTooltipLines.toTypedArray()
        val travelSpeedLabel = t(
            "camping.travelSpeedValue",
            recordOf("speed" to speedBreakdown.partySpeedFeet.toString()),
        )

        val rawHexContents = game.getKingdomActors().firstOrNull()?.getKingdom()?.hexContents ?: emptyArray()
        val hexContentsMap = rawHexContents.associate { raw ->
            raw.hexKey to HexContent(
                id = raw.id,
                hexKey = raw.hexKey,
                type = HexContentType.fromString(raw.type) ?: HexContentType.LANDMARK,
                name = raw.name,
                visibility = HexContentVisibility.fromString(raw.visibility) ?: HexContentVisibility.HIDDEN,
                gmNotes = raw.gmNotes,
                playerText = raw.playerText,
                suppressesEncounters = raw.suppressesEncounters,
                travelModifier = raw.travelModifier,
                linkedQuestId = raw.linkedQuestId,
                linkedUuid = raw.linkedUuid,
                icon = raw.icon
            )
        }

        val hexKeyOptions = getHexKeyOptions()
        val travelStartHexSelect = Select(
            label = t("camping.startHex"),
            name = "travelStartHex",
            value = camping.travelStartHex,
            options = listOf(SelectOption(label = "—", value = "")) + hexKeyOptions,
            stacked = false,
            required = false,
        ).toContext()

        val travelEndHexSelect = Select(
            label = t("camping.endHex"),
            name = "travelEndHex",
            value = camping.travelEndHex,
            options = listOf(SelectOption(label = "—", value = "")) + hexKeyOptions,
            stacked = false,
            required = false,
        ).toContext()

        val startHex = camping.travelStartHex
        val endHex = camping.travelEndHex
        var travelRouteContext: TravelRouteUiContext? = null
        var travelPathError: String? = null

        if (startHex != null && endHex != null && startHex.isNotEmpty() && endHex.isNotEmpty()) {
            // ONE cost model, shared with the "Travel This Route" action below, so the panel can
            // never price a route differently from the way it is actually executed.
            val costModel = travelCostModel()
            val service = TravelService(
                hexContents = hexContentsMap,
                weatherModifier = weatherModifier,
                riverNoBridgeExtraDegrees = costModel.riverExtraDegrees,
                pavedSettlementHexKeys = costModel.pavedSettlementHexKeys,
            )
            // Routes through the SHARED router (the same one kingdom caravan routing uses)
            // rather than a private Dijkstra with an inline copy of the cost rules. No TravelPlan
            // is supplied: the cost model below reads terrain, roads, rivers and settlements
            // itself, so a plan of additive modifiers would be dead weight that looks live —
            // editing it would change nothing, which is how the cost models drifted apart before.
            // Select the path with the SAME cost model that prices it below, so the router
            // cannot optimise one function while the panel reports another — most visibly, it
            // would otherwise route around a paved settlement hex that actually costs 1.
            val path = costModel.routeBetween(startHex, endHex)
                ?: emptyList()
            if (path.isNotEmpty()) {
                // One hexploration activity, in seconds: the 8-hour exploration day divided
                // by the party's activities per day. That already folds in the party Speed, the
                // minimumTravelSpeed override, forced march and the hex size, so the route
                // planner and the hexploration counter above it now agree by construction.
                val route = service.calculateRoute(
                    path = path,
                    secondsPerActivity = getHexplorationActivitySeconds().toDouble(),
                )
                
                val routeModifiersList = mutableListOf<String>()
                if (weatherModifier != 1.0) {
                    routeModifiersList.add(t("camping.weatherModifierLabel", recordOf("value" to weatherModifier.toString())))
                }
                routeModifiersList.add(
                    t(
                        "camping.activitiesPerDayLabel",
                        recordOf("activities" to speedBreakdown.hexplorationActivitiesPerDay.toString()),
                    )
                )
                
                val terrainCounts = mutableMapOf<Terrain, Int>()
                var riverCrossings = 0
                var roadCount = 0
                // Hexes ENTERED, matching the distance and cost above. Including the starting hex
                // made the summary contradict the rest of the panel — it counted the terrain of
                // the hex the party is standing in, and reported a river already beside them as a
                // crossing they were about to make.
                for (hexKey in path.drop(1)) {
                    val hexObj = com.foundryvtt.kingmaker.kingmaker.region.hexes.find { it.key.toString() == hexKey }
                    val terrainName = hexObj?.zone?.terrain
                    val terrain = terrainName?.let { fromCamelCase<Terrain>(it) }
                    if (terrain != null && terrain != Terrain.PLAINS) {
                        terrainCounts[terrain] = (terrainCounts[terrain] ?: 0) + 1
                    }
                    
                    val hexState = com.foundryvtt.kingmaker.kingmaker.state.hexes[hexKey]
                    val features = hexState?.features?.mapNotNull { it.type } ?: emptyList()
                    val hasBridge = features.contains("bridge")
                    if (features.contains("river") && !hasBridge) {
                        riverCrossings++
                    }
                    if (features.contains("road")) {
                        roadCount++
                    }
                }
                
                terrainCounts.forEach { (t, c) ->
                    routeModifiersList.add(t("camping.terrainModifierCount", recordOf(
                        "terrain" to t(t.i18nKey),
                        "count" to c.toString()
                    )))
                }
                if (roadCount > 0) {
                    routeModifiersList.add(t("camping.roadCount", recordOf("count" to roadCount.toString())))
                }
                if (riverCrossings > 0) {
                    routeModifiersList.add(t("camping.riverCount", recordOf("count" to riverCrossings.toString())))
                }
                
                // Route-vs-provisions advisory (display-only): compare the route's whole-day count
                // against the party's durable days of food. Provisions are excluded (wiped each rest).
                val routeDays = kotlin.math.ceil(route.estimatedDurationSeconds / 86400.0).toInt()
                val routeFoodWarning = if (foodForecast.daysOfFood < Int.MAX_VALUE / 2
                    && routeDays > foodForecast.daysOfFood
                ) {
                    t(
                        "camping.routeExceedsFood",
                        recordOf("days" to routeDays, "food" to foodForecast.daysOfFood),
                    )
                } else {
                    null
                }
                travelRouteContext = TravelRouteUiContext(
                    totalCost = route.totalCost,
                    // Hexes TRAVELLED, not hexes occupied: the path includes the hex the
                    // party starts in, so A -> B -> C is three entries but two hexes of travel.
                    totalDistance = (path.size - 1).coerceAtLeast(0),
                    estimatedDuration = formatSeconds(route.estimatedDurationSeconds.toInt()),
                    path = route.path.toTypedArray(),
                    modifiers = routeModifiersList.toTypedArray(),
                    foodWarning = routeFoodWarning,
                    travelSpeedLabel = travelSpeedLabel,
                    travelSpeedTooltip = travelSpeedTooltip,
                    travelSpeedDetails = travelSpeedDetails,
                )
            } else {
                travelPathError = t("camping.noPathFound")
            }
        }
        val companionActivities = camping.getAllActivities().filter { it.requiredCompanion != null }
        CampingSheetContext(
            canRollEncounter = currentRegion?.rollTableUuid != null,
            availableFood = availableFood,
            foodDaysDisplay = foodDaysDisplay,
            foodTonightCovered = foodForecast.tonightCovered,
            totalFoodCost = calculateTotalFoodCost(
                actorMeals = parsedCookingChoices.meals
                    .filter { it.name in uncookedMeals || it.id == "rationsOrSubsistence" },
                foodItems = foodItems,
                availableFood = totalFood,
            ),
            partId = parent.partId,
            recipes = recipesContext,
            terrain = currentTerrain,
            region = Select(
                label = t("camping.region"),
                value = currentRegion?.name,
                options = regions.map { region ->
                    SelectOption(label = regionDropdownLabel(region.name), value = region.name)
                },
                required = true,
                name = "region",
                disabled = !isGM,
                stacked = false,
            ).toContext(),
            pxTimeOffset = pxTimeOffset,
            time = time.toDateInputString(),
            isGM = isGM,
            isDay = time.isDay(),
            prepareCamp = activities.find { it.isPrepareCampsite() },
            activities = activities.filter { !it.isPrepareCampsite() }.toTypedArray(),
            actors = camping.actorUuids.mapNotNull { uuid ->
                actorsByUuid[uuid]?.let { actor ->
                    val meal = if (eatingSection) {
                        chosenMealsByActorUuid[uuid]
                    } else {
                        null
                    }
                    val hungryDays = camping.daysWithoutFoodFor(uuid)
                    CampingSheetActor(
                        hungryDays = hungryDays.takeIf { it > 0 },
                        hungryCritical = starvationSeverity(
                            daysWithoutFood = hungryDays,
                            constitutionModifier = actorConstitutionModifier(actor),
                        ) != StarvationSeverity.NONE,
                        image = actor.img,
                        uuid = uuid,
                        name = actor.name,
                        choseActivity = campingActivitiesSection
                                && groupActivities.any {
                            it.isNotPrepareCamp()
                                    && it.result.actorUuid == uuid
                                    && it.done()
                        },
                        chosenMeal = meal?.name,
                        chosenMealImg = meal?.icon,
                        downtimeHoursRemaining = if (campingActivitiesSection) {
                            camping.downtimeHoursRemaining(uuid)
                        } else null,
                        downtimeHoursMax = if (campingActivitiesSection) {
                            CampingActivityScheduler.MAX_DOWNTIME_HOURS
                        } else null,
                        downtimeBudgetFull = if (campingActivitiesSection) {
                            camping.downtimeHoursRemaining(uuid) <= 0
                        } else null,
                        learnedActivities = run {
                            // Companion activities this actor knows: their own (if they are the
                            // companion) plus any they have learned. Mirrors canActorPerformActivity.
                            val names = companionActivities
                                .filter {
                                    it.isActorRequiredCompanion(actor.name)
                                            || camping.hasActorLearnedActivity(uuid, it.id)
                                }
                                .map { it.name }
                                .distinct()
                                .sorted()
                            if (names.isEmpty()) {
                                null
                            } else {
                                buildString {
                                    append("<strong>")
                                    append(escapeHtml(t("camping.learnedActivitiesHeader")))
                                    append("</strong><ul>")
                                    names.forEach { append("<li>").append(escapeHtml(it)).append("</li>") }
                                    append("</ul>")
                                }
                            }
                        },
                    )
                }
            }.toTypedArray(),
            night = nightModes,
            hexplorationActivityDuration = hexplorationActivityDuration,
            hexplorationActivitiesAvailable = hexplorationActivitiesAvailable,
            travelJournalRows = camping.travelJournalList()
                .asReversed()
                .map { entry ->
                    TravelJournalRow(
                        worldDate = entry.worldDate,
                        kindLabel = t(entry.kind),
                        kindValue = entry.kind.value,
                        detail = listOfNotNull(
                            entry.activityType?.let { key -> if (key.contains('.')) t(key) else key },
                            entry.hexKey?.let { key ->
                                key.toIntOrNull()?.let { "${it / 1000}.${it % 1000}" } ?: key
                            },
                            entry.note,
                        ).joinToString(" — "),
                    )
                }
                .toTypedArray(),
            travelJournalCount = camping.travelJournalList().size,
            hexplorationActivityOptions = HEXPLORATION_ACTIVITY_TYPES
                .map { TravelJournalOption(value = it, label = t(it)) }
                .toTypedArray(),
            hexplorationActivitiesMax = hexplorationActivitiesMax,
            adventuringFor = getAdventuringFor(camping),
            travelingFor = getTravelingFor(camping),
            restDuration = fullRestDuration.total.label,
            restDurationLeft = fullRestDuration.left?.label,
            encounterDc = findEncounterDcModifier(
                camping,
                game.getPF2EWorldTime().time.isDay(),
                weatherDcDelta = game.currentWeatherModifiers(camping).encounterDcDelta,
            ),
            section = t(section),
            prepareCampSection = prepareCampSection,
            campingActivitiesSection = campingActivitiesSection,
            eatingSection = eatingSection,
            needsCookAssignment = eatingSection && parsedCookingChoices.cook == null,
            setWatchesSection = setWatchesSection,
            watchSlots = camping.watchSlots.mapIndexed { index, slotUuids ->
                WatchSlotContext(
                    index = index,
                    hourRange = watchSlotOffsetRange(watchDurationSeconds, camping.watchSlots.size, index)
                        ?.let { (start, end) -> "${formatSeconds(start)} – ${formatSeconds(end)}" },
                    actors = slotUuids.mapNotNull { slotUuid ->
                        actorsByUuid[slotUuid]?.let { act ->
                            // Loot/vehicle camp actors have no Perception statistic — hide the badge.
                            val perception = runCatching { act.perception.mod }.getOrNull()
                            CampingSheetActor(
                                name = act.name,
                                uuid = slotUuid,
                                image = act.img,
                                choseActivity = false,
                                chosenMeal = null,
                                chosenMealImg = null,
                                perceptionLabel = perception?.let { if (it >= 0) "+$it" else "$it" },
                            )
                        }
                    }.toTypedArray(),
                )
            }.toTypedArray(),
            watchWarnings = watchWarnings,
            numberOfWatches = Select(
                label = t("camping.numberOfWatches"),
                name = "numberOfWatches",
                value = camping.watchSlots.size.toString(),
                overrideType = OverrideType.NUMBER,
                options = (minNumberOfWatches..maxNumberOfWatches)
                    .map { SelectOption(label = it.toString(), value = it.toString()) },
                stacked = false,
            ).toContext(),
            isFormValid = isFormValid,
            travelMode = CheckboxInput(
                value = camping.travelModeActive,
                label = t("camping.traveling"),
                name = "travelModeActive",
                elementClasses = if (nightModes.travelMode) listOf("white-checkbox") else listOf("black-checkbox")
            ).toContext(),
            forcedMarch = CheckboxInput(
                value = camping.forcedMarchActive,
                label = t("camping.forcedMarch"),
                name = "forcedMarchActive",
                elementClasses = if (nightModes.forcedMarch) listOf("white-checkbox") else listOf("black-checkbox")
            ).toContext(),
            forcedMarchDays = forcedMarchDays(),
            forcedMarchMaxDays = forcedMarchMaxDays(),
            forcedMarchOverLimit = forcedMarchOverLimit(forcedMarchDays(), forcedMarchMaxDays()),
            sheetBackground = background,
            travelStartHexSelect = travelStartHexSelect,
            travelEndHexSelect = travelEndHexSelect,
            travelRoute = travelRouteContext,
            downtimeProjects = buildDowntimeSectionContext(
                game.getKingdomActors().firstOrNull()?.getKingdom()?.downtimeProjects,
            ) { uuid -> game.actors.find { it.uuid == uuid }?.name },
            travelMoveToken = camping.travelMoveToken == true,
            travelPathError = travelPathError
        )
    }

    fun forcedMarchDays() =
        actor.getCamping()
            ?.secondsSpentForcedMarching
            ?.let {
                max(0, it) / (60 * 60 * 24)
            }
            ?: 0

    fun forcedMarchMaxDays() =
        actor.members
            .filterIsInstance<PF2ECharacter>()
            .minOfOrNull { max(1, it.abilities.con.mod) } ?: 0

    override fun onParsedSubmit(value: CampingSheetFormData): Promise<Void> = buildPromise {
        actor.getCamping()?.let { camping ->
            camping.currentRegion = value.region
            camping.campingActivities = camping.campingActivities
                .asSequence().map { (id, data) ->
                    id to CampingActivity(
                        actorUuid = data.actorUuid,
                        result = value.activities.degreeOfSuccess?.get(id),
                        selectedSkill = value.activities.selectedSkill?.get(id),
                        learnTargetActivityId = value.activities.learnTarget?.get(id),
                    )
                }.toMutableRecord()
            val cookingResultsByRecipe = camping.cooking.results.toMap()
            camping.cooking.results = camping.getAllRecipes().map {
                val result = cookingResultsByRecipe[it.id] ?: CookingResult(
                    result = null,
                    skill = "survival",
                )
                it.id to CookingResult.copy(
                    result,
                    result = value.recipes?.degreeOfSuccess?.get(it.id),
                    skill = value.recipes?.selectedSkill?.get(it.id) ?: "survival",
                )
            }.toMutableRecord()
            camping.travelModeActive = value.travelModeActive
            // Stopping the march does NOT undo it: the accumulated days stand until the party
            // actually rests. Zeroing here meant a mis-click on the checkbox erased the only record
            // of how long they had been pushing.
            camping.forcedMarchActive = value.forcedMarchActive
            camping.travelStartHex = value.travelStartHex
            camping.travelEndHex = value.travelEndHex
            // The control is GM-gated, so it is absent from a player's form. Only honour it from
            // a GM's submit; otherwise any player interaction with the sheet would clear it.
            if (game.user.isGM) camping.travelMoveToken = value.travelMoveToken == true
            ensureWatchSlots(camping, value.numberOfWatches)
            actor.setCamping(camping)
        }
        undefined
    }

    override fun _attachPartListeners(partId: String, htmlElement: HTMLElement, options: ApplicationRenderOptions) {
        super._attachPartListeners(partId, htmlElement, options)
        htmlElement.querySelector("#km-camping-rest")
            ?.takeIfInstance<HTMLButtonElement>()
            ?.ondragstart = {
            it.stopPropagation()
            val data = MacroData(
                name = t("camping.restParty", recordOf("partyName" to actor.name)),
                img = "icons/svg/sleep.svg",
                type = SheetType.CAMPING.value,
                // language=javascript
                command = """
                    game.pf2eKingmakerTools.macros.restMacro('${actor.uuid}');
                """.trimIndent(),
            )
            it.dataTransfer!!.setData("text/plain", JSON.stringify(data))
        }
    }

    /**
     * Every hex on the region map, as start/end options for the route planner.
     *
     * `kingmaker.region.hexes` is a Foundry Collection, so it exposes `contents` and `size` — it
     * has NO `length`. Reading it as an array through a raw `js(...)` handle made `length`
     * undefined, so the old implementation returned an empty list on every call and both
     * dropdowns rendered with nothing but their placeholder. Every other hex lookup in the
     * codebase already goes through `.contents` / `.find`; this one is now the same.
     *
     * Labelled like the expedition destination picker — "Name (col.row)", or just the coordinate
     * when the hex is unnamed — because a bare region key such as "12034" means nothing to a GM.
     */
    /**
     * The one travel cost model: how many Travel activities entering a hex costs, and the routing
     * that follows from it.
     *
     * Shared by the route PREVIEW and the "Travel This Route" ACTION on purpose. Those are the two
     * places that must agree — a panel that prices a journey one way while executing it another is
     * the exact drift this module has repeatedly shipped.
     */
    private inner class TravelCostModel(
        val riverExtraDegrees: Int,
        val pavedSettlementHexKeys: Set<String>,
    ) {
        private val provider = FoundryTravelProvider()

        /** Travel activities to ENTER [hexKey]. */
        fun costOf(hexKey: String): Int {
            val features = provider.getFeaturesForHex(hexKey)
            val unbridged = "river" in features && "bridge" !in features
            return travelActivityCost(
                difficulty = terrainDifficulty(provider.getTerrainForHex(hexKey)),
                hasRoad = "road" in features,
                riverExtraDegrees = if (unbridged) riverExtraDegrees else 0,
                extraDegrees = provider.getContentForHex(hexKey).sumOf { it.travelModifier ?: 0 },
                pavedSettlement = hexKey in pavedSettlementHexKeys,
            )
        }

        /** Cheapest path start..goal inclusive, chosen with the same costs it will be charged. */
        fun routeBetween(startHex: String, endHex: String): List<String> =
            TravelRouter(provider) { _, to, _ -> costOf(to).toDouble() }
                .calculateRoute(startHex, endHex)
                ?.path
                ?: emptyList()

        /** One leg per hex ENTERED — the starting hex is already occupied and costs nothing. */
        fun legsFor(path: List<String>): List<RouteLeg> =
            path.drop(1).map { RouteLeg(hexKey = it, activityCost = costOf(it).toDouble()) }
    }

    /**
     * Reads the two configurable travel house rules and builds the cost model.
     *
     * RAW charges nothing for CROSSING a river (only for travelling along one), so the surcharge
     * comes from travelCostRiverNoBridgeAdditional and is 0 unless the GM opts in. Paved-streets
     * settlements are located by matching settlement names against region hex names, the same key
     * HexGridSync uses to place its markers.
     */
    /**
     * Executes the planned route: advances world time hex by hex, rolls an encounter check for
     * each hex entered, and STOPS where an encounter interrupts the journey.
     *
     * GM-only. The button is template-gated too, but that is presentation — players own the party
     * actor, so the guard here is the real one.
     *
     * The route is recomputed from the current start/end rather than read off the rendered panel,
     * so a stale preview can never be executed, and it goes through the same [travelCostModel] the
     * preview priced, so what the GM confirmed is what actually happens.
     */
    private suspend fun travelPlannedRoute(moveToken: Boolean) {
        if (!game.user.isGM) {
            ui.notifications.warn(t("camping.travelRouteGmOnly"))
            return
        }
        val camping = actor.getCamping() ?: return
        val startHex = camping.travelStartHex?.takeIf { it.isNotBlank() }
        val endHex = camping.travelEndHex?.takeIf { it.isNotBlank() }
        if (startHex == null || endHex == null) {
            ui.notifications.warn(t("camping.travelRouteNoRoute"))
            return
        }

        val costModel = travelCostModel()
        val path = costModel.routeBetween(startHex, endHex)
        val legs = costModel.legsFor(path)
        if (legs.isEmpty()) {
            ui.notifications.warn(t("camping.travelRouteNoRoute"))
            return
        }

        val plan = splitRouteIntoDays(legs, getHexplorationActivities())
        val confirmed = confirm(
            t(
                "camping.confirmTravelRoute",
                recordOf("hexes" to plan.hexesEntered.toString(), "days" to plan.totalDays.toString()),
            )
        )
        if (!confirmed) return

        val secondsPerActivity = getHexplorationActivitySeconds()
        var legsCompleted = 0
        var stoppedAt: String? = null

        legLoop@ for (day in plan.days) {
            for (leg in day.legs) {
                // Seasons & Stars can throw here on a misconfigured calendar (see Resting.kt,
                // df09f4a3). Stop the journey rather than silently travelling free hexes.
                val seconds = (leg.activityCost * secondsPerActivity).roundToInt()
                val advanced = runCatching { game.time.advance(seconds).await() }.isSuccess
                if (!advanced) {
                    ui.notifications.error(t("camping.travelRouteTimeAdvanceFailed"))
                    stoppedAt = leg.hexKey
                    break@legLoop
                }
                legsCompleted++
                if (moveToken) moveCampingTokenToHex(leg.hexKey)
                // NOTE: rollRandomEncounter derives the hex it checks from the party TOKEN's
                // position (getPartyCurrentHexKey) and takes no hex parameter. So the per-hex
                // encounter DC and the hex-state suppression filter only follow the journey when
                // the token is being moved; with the checkbox off, every check resolves against
                // the hex the party token is standing in. Moving the token first, above, is what
                // makes the check match the leg.
                // An encounter ends the journey where it happened; the GM resumes by planning a
                // new route from there.
                if (rollRandomEncounter(game, actor, includeFlatCheck = true)) {
                    stoppedAt = leg.hexKey
                    break@legLoop
                }
            }
        }

        val summary = summarizeTravelExecution(plan, legsCompleted, stoppedAt)
        // Re-read before writing. getCamping() hands back a deepClone, and the leg loop's
        // rollRandomEncounter does its OWN getCamping/setCamping -- so `camping` above is a stale
        // copy taken before the journey started. Saving it here silently discarded everything the
        // encounter path had just persisted, most visibly lastEncounterCategory/lastEncounterResult,
        // which meant an encounter that stopped a route lost its own preview data.
        val latest = actor.getCamping() ?: camping
        // The party ends the journey wherever it actually stopped.
        latest.travelStartHex = path.getOrNull(legsCompleted) ?: path.lastOrNull()
        actor.setCamping(latest)
        postTravelSummary(summary, destinationHexKey = path.lastOrNull())
    }

    /**
     * Moves the party token to [hexKey]'s hex on the active scene.
     *
     * Follows the one verified token-move in the repo (DailyTickHooks companion travel): resolve
     * the Kingmaker hex's grid offset, build a GridHex against the active hexagonal grid, and use
     * its topLeft as the token's x/y. Token writes are GM-only, which the caller has already
     * checked.
     *
     * Best-effort: a non-hex scene, a missing token or an unresolvable hex simply leaves the token
     * where it is rather than aborting the journey.
     */
    private suspend fun moveCampingTokenToHex(hexKey: String) {
        val scene = game.scenes.active ?: return
        if (!scene.grid.isHexagonal) return
        val tokenDoc = scene.tokens.contents.find { it.actorId == actor.id } ?: return
        val hexObj = runCatching { kingmaker.region.hexes.find { it.key.toString() == hexKey } }
            .getOrNull() ?: return
        // Centre of the hex via the scene grid, then back off half a tile to get the token's
        // top-left — the exact inverse of how getPartyCurrentHexKey derives a token's centre.
        // Uses BaseGrid.getCenterPoint rather than constructing a GridHex: that external is
        // @JsQualifier("foundry.grid") and resolving it at module load breaks outside Foundry.
        val center = runCatching { scene.grid.getCenterPoint(hexObj.offset) }.getOrNull() ?: return
        runCatching {
            tokenDoc.typeSafeUpdate {
                x = center.x - scene.grid.sizeX / 2.0
                y = center.y - scene.grid.sizeY / 2.0
            }
        }
    }

    /** Posts the travel summary card: how far the party got, and whether something stopped them. */
    private suspend fun postTravelSummary(summary: TravelExecutionSummary, destinationHexKey: String?) {
        fun label(hexKey: String?) = hexKey?.let { formatHexKeyLabel(it) ?: it } ?: ""
        val text = when {
            summary.stoppedAtHexKey != null && summary.hexesEntered == 0 ->
                t("chatMessages.travelRoute.noProgress", recordOf("stoppedAt" to label(summary.stoppedAtHexKey)))
            summary.stoppedAtHexKey != null ->
                t(
                    "chatMessages.travelRoute.interrupted",
                    recordOf(
                        "hexes" to summary.hexesEntered.toString(),
                        "days" to summary.daysElapsed.toString(),
                        "stoppedAt" to label(summary.stoppedAtHexKey),
                    ),
                )
            else ->
                t(
                    "chatMessages.travelRoute.arrived",
                    recordOf(
                        "hexes" to summary.hexesEntered.toString(),
                        "days" to summary.daysElapsed.toString(),
                        "destination" to label(destinationHexKey),
                    ),
                )
        }
        val context = js("{}")
        context.summary = text
        context.encounterStopped = summary.stoppedAtHexKey != null
        postChatTemplate(templatePath = "chatmessages/travel-route-summary.hbs", templateContext = context)
    }

    private fun travelCostModel(): TravelCostModel {
        val pavedKeys = if (Pfrpg2eKingdomCampingWeatherSettings.getPavedStreetsReduceTravelCost()) {
            runCatching {
                val paved = game.getKingdomActors().firstOrNull()
                    ?.getKingdom()
                    ?.getAllSettlements(game)
                    ?.allSettlements
                    .orEmpty()
                    .filter { it.pavedStreets }
                    .map { it.name.lowercase().trim() }
                    .toSet()
                kingmaker.region.hexes.contents
                    .filter { it.name.lowercase().trim() in paved }
                    .map { it.key.toString() }
                    .toSet()
            }.getOrDefault(emptySet())
        } else {
            emptySet()
        }
        return TravelCostModel(
            riverExtraDegrees = Pfrpg2eKingdomCampingWeatherSettings.getTravelCostRiverNoBridgeAdditional(),
            pavedSettlementHexKeys = pavedKeys,
        )
    }

    private fun getHexKeyOptions(): List<SelectOption> = runCatching {
        kingmaker.region.hexes.contents
            .map { hex ->
                val key = hex.key.toString()
                val coordinate = formatHexKeyLabel(key) ?: key
                val name = hex.name
                val label = if (name.isNotBlank() && name != coordinate) "$name ($coordinate)" else coordinate
                SelectOption(value = key, label = label)
            }
            .sortedBy { it.label }
    }.getOrDefault(emptyList())

}

fun beginRest(actor: CampingActor, dispatcher: ActionDispatcher) {
    actor.getCamping()?.let { camping ->
        if (camping.watchSecondsRemaining > 0) {
            // continue watch
            buildPromise {
                rest(
                    game = game,
                    dispatcher = dispatcher,
                    campingActor = actor,
                    camping = camping,
                    skipWatch = false,
                    skipDailyPreparations = false,
                    disableRandomEncounter = false,
                    skipWeather = camping.restSettings.skipWeather,
                    party = actor,
                )
            }
        } else {
            ConfirmWatchApplication(
                camping = camping,
                game = game,
            ) { enableWatch, enableDailyPreparations, checkRandomEncounter, checkWeather ->
                buildPromise {
                    rest(
                        game = game,
                        dispatcher = dispatcher,
                        campingActor = actor,
                        camping = camping,
                        skipWatch = !enableWatch,
                        skipDailyPreparations = !enableDailyPreparations,
                        disableRandomEncounter = !checkRandomEncounter,
                        skipWeather = !checkWeather,
                        party = actor,
                    )
                }
            }.launch()
        }
    }
}


private fun getActivitySkills(
    actor: PF2ECreature?,
    groupedActivity: ActivityAndData,
    ignoreSkillRequirements: Boolean,
): FormElementContext {
    return groupedActivity.data.getCampingSkills(actor).let { skillsAndProficiencies ->
        val options = skillsAndProficiencies
            .filter {
                if (ignoreSkillRequirements || actor == null) {
                    true
                } else {
                    actor.satisfiesSkillRequirement(it)
                }
            }
            .filter { !it.validateOnly }
            .map {
                SelectOption(
                    label = t(it.attribute),
                    value = it.attribute.value,
                    classes = listOf("km-proficiency-${it.proficiency.toCamelCase()}")
                )
            }
            .sortedBy { it.label }
        Select(
            label = t("camping.selectedSkill"),
            name = "activities.selectedSkill.${groupedActivity.data.id}",
            hideLabel = true,
            options = options,
            elementClasses = listOf("km-proficiency"),
            value = groupedActivity.result.selectedSkill,
        ).toContext()
    }
}

/**
 * Builds the "Learn from a Companion" dropdown: every companion activity whose required
 * companion is currently in camp and that has not already been learned. Picking one and
 * succeeding on the activity adds it to [CampingData.learnedCompanionActivities].
 */
private fun getLearnTargetSelect(
    activityId: String,
    camping: CampingData,
    actorUuid: String?,
    presentActorNames: Set<String>,
    selected: String?,
): FormElementContext {
    val learned = actorUuid?.let { uuid ->
        val key = uuid.replace('.', '_')
        val actorLearned = camping.learnedCompanionActivitiesByActor?.get(key)?.toSet() ?: emptySet()
        actorLearned + camping.learnedCompanionActivities.toSet()
    } ?: camping.learnedCompanionActivities.toSet()
    val options = camping.getAllActivities()
        .filter { activity ->
            activity.requiredCompanion != null
                    && activity.id !in learned
                    && activity.isRequiredCompanionPresent(presentActorNames)
        }
        .map { activity ->
            SelectOption(
                label = t(
                    "camping.learnTargetOption",
                    recordOf(
                        "activity" to activity.name,
                        "companion" to (activity.requiredCompanion ?: ""),
                    ),
                ),
                value = activity.id,
            )
        }
        .sortedBy { it.label }
    return Select(
        label = t("camping.learnTarget"),
        name = "activities.learnTarget.$activityId",
        hideLabel = true,
        options = options,
        required = false,
        value = selected,
        elementClasses = listOf("km-learn-target"),
    ).toContext()
}

suspend fun openOrCreateCampingSheet(game: Game, dispatcher: ActionDispatcher, actor: CampingActor) {
    if (actor.getCamping() == null) {
        actor.setCamping(getDefaultCamping(game))
        openJournal("Compendium.pf2e-kingmaker-tools.kingmaker-tools-journals.JournalEntry.kd8cT1Uv9hZOrpgS")
        actor.update(recordOf("ownership" to actor.ownershipOwnersOnly())).await()
    }
    CampingSheet(game, actor, dispatcher).launch()
}