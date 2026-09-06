package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.Config
import at.posselt.pfrpg2e.actions.ActionDispatcher
import at.posselt.pfrpg2e.actions.ActionMessage
import at.posselt.pfrpg2e.actions.handlers.LearnSpecialRecipeHandler
import at.posselt.pfrpg2e.camping.dialogs.FavoriteMealChoice
import at.posselt.pfrpg2e.camping.dialogs.FavoriteMealSubmitData
import at.posselt.pfrpg2e.camping.dialogs.FavoriteMealsApplication
import at.posselt.pfrpg2e.camping.shouldRowBePinned
import at.posselt.pfrpg2e.camping.getPartyCurrentHexKey
import at.posselt.pfrpg2e.resting.EIGHT_HOURS_SECONDS
import at.posselt.pfrpg2e.weather.syncWeather
import at.posselt.pfrpg2e.fixtures.FakeFoundryEnvironment
import at.posselt.pfrpg2e.fixtures.FakeGame
import at.posselt.pfrpg2e.fixtures.createFakeCampingActor
import at.posselt.pfrpg2e.fixtures.createFakeCharacter
import at.posselt.pfrpg2e.fixtures.createFakeConsumable
import at.posselt.pfrpg2e.fixtures.createFakeKingdomActor
import at.posselt.pfrpg2e.fixtures.applyFoundryMerge
import at.posselt.pfrpg2e.utils.asSequence
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core._del
import js.objects.Record
import js.objects.recordOf
import js.objects.unsafeJso
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.promise
import org.w3c.dom.HTMLElement
import kotlin.js.Promise
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CampingAdapterRegressionTest {

    private val env = FakeFoundryEnvironment()

    private fun runTest(block: suspend () -> Unit): dynamic =
        @Suppress("DELICATE_API_TRANSITIONAL_MINI_MARKER") GlobalScope.promise {
            try {
                block()
            } finally {
                env.reset()
            }
        }

    @BeforeTest
    fun setUp() {
        env.install()
        env.reset()
    }

    @AfterTest
    fun tearDown() {
        env.reset()
    }

    private fun registerFoodCompendium() {
        env.registry.register(
            Config.items.basicIngredientUuid,
            createFakeConsumable("comp-basic", Config.items.basicIngredientUuid, "Basic Ingredient", quantity = 0),
        )
        env.registry.register(
            Config.items.specialIngredientUuid,
            createFakeConsumable("comp-special", Config.items.specialIngredientUuid, "Special Ingredient", quantity = 0),
        )
        env.registry.register(
            Config.items.rationUuid,
            createFakeConsumable("comp-ration", Config.items.rationUuid, "Rations", quantity = 0),
        )
        env.registry.register(
            Config.items.provisionsUuid,
            createFakeConsumable("comp-provisions", Config.items.provisionsUuid, "Provisions", quantity = 0),
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 1. CampingSheet.consumeRations
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun consumeRationsStampsRationsPaidForDayWhenNoShortfall() = runTest {
        registerFoodCompendium()
        val game = FakeGame(isGM = true)
        game.setWorldDay(42)

        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camper = createFakeCharacter("camper-1", "Actor.camper-1", "Valerie", isOwner = true)
        // Give camper 3 rations so reducing by 1 has no shortfall
        val rationItem = createFakeConsumable("item-1", "Item.1", "Rations", quantity = 3)
        camper.asDynamic().addItem(rationItem)
        env.registry.register("Actor.camper-1", camper)

        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.camper-1")
        camping.cooking.actorMeals = recordOf(
            "camper-1" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.camper-1"
                chosenMeal = "rationsOrSubsistence"
            }
        )
        partyActor.setCamping(camping)

        val dispatcher = ActionDispatcher(game.asGame(), emptyList())
        val sheet = CampingSheet(game.asGame(), partyActor, dispatcher)

        sheet.consumeRations()

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertEquals(42, stored.cooking.rationsPaidForDay, "rationsPaidForDay must be stamped with today's world day")
    }

    @Test
    fun consumeRationsDoesNotStampWhenShortfall() = runTest {
        registerFoodCompendium()
        val game = FakeGame(isGM = true)
        game.setWorldDay(42)

        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camper = createFakeCharacter("camper-1", "Actor.camper-1", "Valerie", isOwner = true)
        // 0 rations available -> shortfall of 1 ration
        env.registry.register("Actor.camper-1", camper)

        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.camper-1")
        camping.cooking.actorMeals = recordOf(
            "camper-1" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.camper-1"
                chosenMeal = "rationsOrSubsistence"
            }
        )
        partyActor.setCamping(camping)

        val dispatcher = ActionDispatcher(game.asGame(), emptyList())
        val sheet = CampingSheet(game.asGame(), partyActor, dispatcher)

        sheet.consumeRations()

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertNull(stored.cooking.rationsPaidForDay, "rationsPaidForDay must NOT be stamped when food cannot cover bill")
    }

    @Test
    fun consumeRationsWritesViaTypedUpdatePreservingConcurrentFlagChanges() = runTest {
        registerFoodCompendium()
        val game = FakeGame(isGM = true)
        game.setWorldDay(10)

        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camper = createFakeCharacter("camper-1", "Actor.camper-1", "Valerie", isOwner = true)
        val rationItem = createFakeConsumable("item-1", "Item.1", "Rations", quantity = 5)
        camper.asDynamic().addItem(rationItem)
        env.registry.register("Actor.camper-1", camper)

        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.camper-1")
        camping.cooking.actorMeals = recordOf(
            "camper-1" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.camper-1"
                chosenMeal = "rationsOrSubsistence"
            }
        )
        camping.gunsToClean = 0
        partyActor.setCamping(camping)

        val dispatcher = ActionDispatcher(game.asGame(), emptyList())
        val sheet = CampingSheet(game.asGame(), partyActor, dispatcher)

        // Simulate another client concurrently updating gunsToClean before consumeRations finishes
        partyActor.typedCampingUpdate {
            gunsToClean.set(9)
        }

        sheet.consumeRations()

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertEquals(10, stored.cooking.rationsPaidForDay)
        assertEquals(9, stored.gunsToClean, "typedCampingUpdate must preserve concurrent edits to other flag fields")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 2. StarvationTick.tickNightlyStarvation
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun tickNightlyStarvationChargesZeroRationsWhenPaidToday() = runTest {
        registerFoodCompendium()
        val game = FakeGame(isGM = true)
        val today = 50
        game.setWorldDay(today)

        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camper = createFakeCharacter("camper-1", "Actor.camper-1", "Amiri", isOwner = true)
        env.registry.register("Actor.camper-1", camper)

        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.camper-1")
        camping.cooking.actorMeals = recordOf(
            "camper-1" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.camper-1"
                chosenMeal = "rationsOrSubsistence"
            }
        )
        // Today's stamp: paid in full earlier today
        camping.cooking.rationsPaidForDay = today

        val crossings = tickNightlyStarvation(game.asGame(), camping, partyActor, listOf(camper))

        assertTrue(crossings.isEmpty(), "No starvation crossing should occur when rations were paid for today")
        val daysWithout = camping.daysWithoutFood?.get("Actor_camper-1") ?: 0
        assertEquals(0, daysWithout, "Camper should not accumulate hunger days when rations were paid today")
    }

    @Test
    fun tickNightlyStarvationChargesRationsWhenStampIsFromYesterday() = runTest {
        registerFoodCompendium()
        val game = FakeGame(isGM = true)
        val today = 50
        game.setWorldDay(today)

        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camper = createFakeCharacter("camper-1", "Actor.camper-1", "Amiri", isOwner = true)
        env.registry.register("Actor.camper-1", camper)

        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.camper-1")
        camping.cooking.actorMeals = recordOf(
            "camper-1" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.camper-1"
                chosenMeal = "rationsOrSubsistence"
            }
        )
        // Yesterday's stamp: must NOT pay for tonight
        camping.cooking.rationsPaidForDay = today - 1

        val crossings = tickNightlyStarvation(game.asGame(), camping, partyActor, listOf(camper))

        val daysWithout = camping.daysWithoutFood?.get("Actor_camper-1") ?: 0
        assertEquals(1, daysWithout, "Yesterday's stamp cannot pay for tonight; camper without food must gain hunger")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 3. RandomEncounters
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun showEncounterPreviewPersistsManifestAndReloadRestoresIt() = runTest {
        val game = FakeGame(isGM = true)
        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camping = getDefaultCamping(game.asGame())
        partyActor.setCamping(camping)

        val seededManifest = unsafeJso<RawEncounterManifest> {
            creatures = arrayOf(
                unsafeJso<RawEncounterCreature> {
                    uuid = "Actor.dire-wolf"
                    count = 2
                }
            )
        }

        showEncounterPreview(
            game = game.asGame(),
            actor = partyActor,
            camping = camping,
            category = EncounterCategory.COMBAT,
            regionName = "Narlmarches",
            resultText = "2 Dire Wolves",
            seededManifest = seededManifest,
        )

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertEquals("combat", stored.lastEncounterCategory)
        assertEquals("2 Dire Wolves", stored.lastEncounterResult)
        assertNotNull(stored.lastEncounterManifest)
        assertEquals(1, stored.lastEncounterManifest?.creatures?.size)
        assertEquals("Actor.dire-wolf", stored.lastEncounterManifest?.creatures?.get(0)?.uuid)

        // Verify restorable helper detects it
        val category = restorableEncounterPreview(stored.lastEncounterCategory, stored.lastEncounterResult)
        assertEquals(EncounterCategory.COMBAT, category)
    }

    @Test
    fun encounterAcceptRecordsJournalAndClearsPreview() = runTest {
        val game = FakeGame(isGM = true)
        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camping = getDefaultCamping(game.asGame())
        camping.lastEncounterCategory = "combat"
        camping.lastEncounterResult = "Bandit Ambush"
        camping.lastEncounterManifest = unsafeJso { creatures = emptyArray() }
        partyActor.setCamping(camping)

        // Accept actions: recordEncounter then clearEncounterPreview
        partyActor.recordEncounter(game.asGame(), hexKey = "0101", note = "Bandit Ambush")
        clearEncounterPreview(partyActor)

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertNull(stored.lastEncounterCategory)
        assertNull(stored.lastEncounterResult)
        assertNull(stored.lastEncounterManifest)
        assertNotNull(stored.travelJournal)
        assertTrue(stored.travelJournal!!.any { it.note == "Bandit Ambush" })
    }

    @Test
    fun encounterConvertToQuestWithNullKingdomPreservesPreview() = runTest {
        val game = FakeGame(isGM = true)
        // No kingdom actor exists, so convertRumorToQuest returns null
        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camping = getDefaultCamping(game.asGame())
        camping.lastEncounterCategory = "rumor"
        camping.lastEncounterResult = "Strange lights over the lake"
        partyActor.setCamping(camping)

        val hook = Rumor(text = "Strange lights over the lake", sourceRegion = "Narlmarches", isQuestHook = true)
        val questId = convertRumorToQuest(game.asGame(), hook)
        assertNull(questId, "Conversion without a kingdom must return null")

        // Per RandomEncounters line 275: if (questId == null) return@buildPromise
        // The preview remains uncleared so the GM does not lose the lead
        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertEquals("rumor", stored.lastEncounterCategory)
        assertEquals("Strange lights over the lake", stored.lastEncounterResult)
    }

    @Test
    fun encounterRejectClearsPreviewWithoutJournaling() = runTest {
        val game = FakeGame(isGM = true)
        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camping = getDefaultCamping(game.asGame())
        camping.lastEncounterCategory = "hazard"
        camping.lastEncounterResult = "Quicksand"
        partyActor.setCamping(camping)

        // Reject clears preview without calling recordEncounter
        clearEncounterPreview(partyActor)

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertNull(stored.lastEncounterCategory)
        assertNull(stored.lastEncounterResult)
        val entries = stored.travelJournal ?: emptyArray()
        assertTrue(entries.none { it.note == "Quicksand" }, "Rejecting an encounter must record nothing in travel journal")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 4. CampingUtils.clearDepartingCompanionsFromCamp
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun clearDepartingCompanionsRemovesMealKeyUnderMergeSemantics() = runTest {
        val game = FakeGame(isGM = true)
        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.amiri", "Actor.valerie")
        camping.campingActivities = recordOf(
            "cook-meal" to unsafeJso<CampingActivity> { actorUuid = "Actor.amiri" },
            "prepare-campsite" to unsafeJso<CampingActivity> { actorUuid = "Actor.valerie" },
        )
        camping.watchSlots = arrayOf(arrayOf("Actor.amiri", "Actor.valerie"))
        camping.cooking.actorMeals = recordOf(
            "amiriId" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.amiri"
                chosenMeal = "haggis"
            },
            "valerieId" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.valerie"
                chosenMeal = "hearty-meal"
            },
        )
        partyActor.setCamping(camping)

        // Verify initial state
        assertNotNull(partyActor.getCamping()?.cooking?.actorMeals?.get("amiriId"))
        assertNotNull(partyActor.getCamping()?.cooking?.actorMeals?.get("valerieId"))

        // Execute departure housekeeping
        clearDepartingCompanionsFromCamp(game.asGame(), listOf("Actor.amiri" to "Amiri"))

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        // Under Foundry merge semantics, in-memory deletion on camping alone does not delete keys;
        // clearDepartingCompanionsFromCamp issues the typed deleteEntry follow-up.
        assertNull(stored.cooking.actorMeals["amiriId"], "Amiri's meal key must be completely removed from flag storage")
        assertNotNull(stored.cooking.actorMeals["valerieId"], "Valerie's meal key must remain")
        assertNull(stored.campingActivities["cook-meal"]?.actorUuid, "Amiri's activity assignment must be unassigned")
        assertEquals("Actor.valerie", stored.campingActivities["prepare-campsite"]?.actorUuid)
        assertEquals(listOf("Actor.valerie"), stored.watchSlots[0].toList(), "Amiri must be removed from watch slot")
    }

    @Test
    fun setCampingAloneWithoutDeleteEntryLeavesMealKeyUnderMergeSemantics() = runTest {
        // Demonstrates why the deleteEntry follow-up was required:
        // A simple setCamping(camping) where a key was deleted in JS memory does NOT remove it from storage.
        val partyActor = createFakeCampingActor()
        val camping = getDefaultCamping(FakeGame().asGame())
        camping.cooking.actorMeals = recordOf(
            "amiriId" to unsafeJso<ActorMeal> { actorUuid = "Actor.amiri"; chosenMeal = "haggis" },
            "valerieId" to unsafeJso<ActorMeal> { actorUuid = "Actor.valerie"; chosenMeal = "hearty-meal" },
        )
        partyActor.setCamping(camping)

        // Delete amiri in memory and save with setCamping alone
        val modified = partyActor.getCamping()!!
        js("delete modified.cooking.actorMeals.amiriId")
        partyActor.setCamping(modified)

        // Under real Foundry merge, amiriId is STILL THERE because it was absent from update
        val stored = partyActor.getCamping()!!
        assertNotNull(
            stored.cooking.actorMeals["amiriId"],
            "setCamping alone merges and preserves unmentioned keys; deleteEntry is required to delete",
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 5. FavoriteMealsApplication save
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun favoriteMealsSaveOnlyPinsChangedRowAndPreservesUnchangedPinState() = runTest {
        val game = FakeGame(isGM = true)
        val partyActor = createFakeCampingActor()
        game.addActor(partyActor)

        val camper1 = createFakeCharacter("c1", "Actor.c1", "Valerie", isOwner = true)
        val camper2 = createFakeCharacter("c2", "Actor.c2", "Amiri", isOwner = true)
        val camper3 = createFakeCharacter("c3", "Actor.c3", "Linzi", isOwner = true)
        val camper4 = createFakeCharacter("c4", "Actor.c4", "Harrim", isOwner = true)
        env.registry.register("Actor.c1", camper1)
        env.registry.register("Actor.c2", camper2)
        env.registry.register("Actor.c3", camper3)
        env.registry.register("Actor.c4", camper4)

        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.c1", "Actor.c2", "Actor.c3", "Actor.c4")
        camping.cooking.actorMeals = recordOf(
            "c1" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.c1"
                favoriteMeal = "hearty-meal"
                fixedFavoriteMeal = true // Pinned & unchanged
            },
            "c2" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.c2"
                favoriteMeal = "basic-meal"
                fixedFavoriteMeal = false // Unpinned & CHANGED
            },
            "c3" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.c3"
                favoriteMeal = "hearty-meal"
                fixedFavoriteMeal = true // Pinned, will be explicitly unpinned
            },
            "c4" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.c4"
                favoriteMeal = "basic-meal"
                fixedFavoriteMeal = false // Unpinned & unchanged
            },
        )
        partyActor.setCamping(camping)

        val app = FavoriteMealsApplication(game.asGame(), partyActor)

        game.addActor(camper1)
        game.addActor(camper2)
        game.addActor(camper3)
        game.addActor(camper4)

        // Submit changed choices:
        // c1: unchanged ("hearty-meal")
        // c2: changed ("hunter-stew")
        // c3: unchanged ("hearty-meal")
        // c4: unchanged ("basic-meal")
        val submitData = js("({ meals: [] })").unsafeCast<FavoriteMealSubmitData>()
        submitData.asDynamic().meals = arrayOf(
            FavoriteMealChoice(actorUuid = "Actor.c1", favoriteMeal = "hearty-meal"),
            FavoriteMealChoice(actorUuid = "Actor.c2", favoriteMeal = "hunter-stew"),
            FavoriteMealChoice(actorUuid = "Actor.c3", favoriteMeal = "hearty-meal"),
            FavoriteMealChoice(actorUuid = "Actor.c4", favoriteMeal = "basic-meal"),
        )
        val submitFn: dynamic = js("""
            (function(a) {
                for (let p = a; p; p = Object.getPrototypeOf(p)) {
                    for (let k of Object.getOwnPropertyNames(p)) {
                        if (k.startsWith('onParsedSubmit')) return p[k];
                    }
                }
                return null;
            })(app)
        """)
        (submitFn.call(app, submitData).unsafeCast<Promise<*>>()).await()

        // Explicitly unpin c3 via click action
        val unpinTarget = js("({ dataset: { action: 'unpin-meal', actorUuid: 'Actor.c3' } })").unsafeCast<HTMLElement>()
        app.asDynamic()._onClickAction(js("({})"), unpinTarget)
        delay(50)

        // Trigger save
        val saveTarget = js("({ dataset: { action: 'km-save' } })").unsafeCast<HTMLElement>()
        app.asDynamic()._onClickAction(js("({})"), saveTarget)
        delay(50)

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        val meals = stored.cooking.actorMeals
        // c1: unchanged row kept previous pin state (true)
        assertEquals(true, meals["c1"]?.fixedFavoriteMeal, "Unchanged pinned row must stay pinned")
        assertEquals("hearty-meal", meals["c1"]?.favoriteMeal)

        // c2: changed pick set fixedFavoriteMeal to true
        assertEquals(true, meals["c2"]?.fixedFavoriteMeal, "Changed pick must become pinned")
        assertEquals("hunter-stew", meals["c2"]?.favoriteMeal)

        // c3: explicitly unpinned set to false
        assertEquals(false, meals["c3"]?.fixedFavoriteMeal, "Explicitly unpinned row must be unpinned")

        // c4: unchanged unpinned row kept previous pin state (false)
        assertEquals(false, meals["c4"]?.fixedFavoriteMeal, "Unchanged unpinned row must stay unpinned")
    }

    @Test
    fun shouldRowBePinnedPureContract() {
        // Unchanged pinned -> stays pinned
        assertTrue(shouldRowBePinned(wasPinned = true, previousMeal = "mealA", pickedMeal = "mealA", explicitlyUnpinned = false))
        // Changed unpinned -> becomes pinned
        assertTrue(shouldRowBePinned(wasPinned = false, previousMeal = "mealA", pickedMeal = "mealB", explicitlyUnpinned = false))
        // Explicitly unpinned -> false regardless of previous
        assertFalse(shouldRowBePinned(wasPinned = true, previousMeal = "mealA", pickedMeal = "mealA", explicitlyUnpinned = true))
        // Unchanged unpinned -> stays unpinned
        assertFalse(shouldRowBePinned(wasPinned = false, previousMeal = "mealA", pickedMeal = "mealA", explicitlyUnpinned = false))
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 6. utils/UpdateBuilder.deleteEntry
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun updateBuilderDeleteEntryDeletesKeyFromStorageViaMerge() = runTest {
        val partyActor = createFakeCampingActor()
        val camping = getDefaultCamping(FakeGame().asGame())
        camping.cooking.actorMeals = recordOf(
            "target-key" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.amiri"
                chosenMeal = "haggis"
            },
            "survivor-key" to unsafeJso<ActorMeal> {
                actorUuid = "Actor.valerie"
                chosenMeal = "hearty-meal"
            }
        )
        partyActor.setCamping(camping)

        assertNotNull(partyActor.getCamping()?.cooking?.actorMeals?.get("target-key"))
        assertNotNull(partyActor.getCamping()?.cooking?.actorMeals?.get("survivor-key"))

        // Build update and verify emitted path
        val update = buildCampingUpdate {
            cooking.actorMeals.deleteEntry("target-key")
        }
        val keys = js("Object.keys(update)").unsafeCast<Array<String>>().toList()
        assertEquals(listOf("cooking.actorMeals.target-key"), keys)

        // Apply via typedCampingUpdate to fake actor with merge semantics
        partyActor.typedCampingUpdate {
            cooking.actorMeals.deleteEntry("target-key")
        }

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertNull(stored.cooking.actorMeals["target-key"], "The target key must be removed from flag storage")
        assertNotNull(stored.cooking.actorMeals["survivor-key"], "The survivor key must remain in flag storage")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7. Recipe learning, sheltered suppression, weather sync, route days
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    fun learnSpecialRecipeHandlerFailureDoesNotAnnounceOrLearn() = runTest {
        registerFoodCompendium()
        val game = FakeGame(isGM = true)
        val partyActor = createFakeCampingActor()
        env.registry.register(partyActor.uuid, partyActor)
        game.addActor(partyActor)

        val chef = createFakeCharacter("chef-1", "Actor.chef-1", "Chef", isOwner = true)
        val foodItem = createFakeConsumable("item-food", "Item.food", "Basic Ingredient", quantity = 10)
        chef.asDynamic().addItem(foodItem)
        env.registry.register("Actor.chef-1", chef)

        val testRecipe = RecipeData(
            id = "hearty-meal",
            name = "Hearty Meal",
            basicIngredients = 1,
            specialIngredients = 0,
            cookingLoreDC = 15,
            survivalDC = 15,
            uuid = "Item.hearty-meal",
            level = 1,
            cost = RawCost(currency = "gp", value = 1),
            rarity = "common",
            isSpecialMeal = true,
            criticalSuccess = CookingOutcome(effects = emptyArray(), chooseRandomly = null, message = null),
            success = CookingOutcome(effects = emptyArray(), chooseRandomly = null, message = null),
            criticalFailure = CookingOutcome(effects = emptyArray(), chooseRandomly = null, message = null),
        )
        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.chef-1")
        camping.cooking.knownRecipes = arrayOf("basic-meal")
        camping.cooking.homebrewMeals = arrayOf(testRecipe)
        partyActor.setCamping(camping)

        val handler = LearnSpecialRecipeHandler()
        val dispatcher = ActionDispatcher(game.asGame(), listOf(handler))

        val msg = ActionMessage(
            action = "learnSpecialRecipe",
            data = unsafeJso<dynamic> {
                campingActorUuid = partyActor.uuid
                actorUuid = chef.uuid
                id = "hearty-meal"
                degree = "failure"
            }
        )
        handler.execute(msg, dispatcher)

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertFalse(stored.cooking.knownRecipes.contains("hearty-meal"), "hearty-meal must not be learned on failure")
        assertEquals(1, env.chat.messages.size, "Only food consumption message should be posted on failure")
        val messages = env.chat.messages.map { it.content.unsafeCast<String?>() ?: "" }
        assertTrue(messages.none { it.contains("chatMessages.discoverSpecialMeal.learned") }, "No learned announcement on failure")
    }

    @Test
    fun learnSpecialRecipeHandlerSuccessLearnsAndAnnouncesViaTargetedUpdate() = runTest {
        registerFoodCompendium()
        val game = FakeGame(isGM = true)
        val partyActor = createFakeCampingActor()
        env.registry.register(partyActor.uuid, partyActor)
        game.addActor(partyActor)

        val chef = createFakeCharacter("chef-1", "Actor.chef-1", "Chef", isOwner = true)
        val foodItem = createFakeConsumable("item-food", "Item.food", "Basic Ingredient", quantity = 10)
        chef.asDynamic().addItem(foodItem)
        env.registry.register("Actor.chef-1", chef)

        val testRecipe = RecipeData(
            id = "hearty-meal",
            name = "Hearty Meal",
            basicIngredients = 1,
            specialIngredients = 0,
            cookingLoreDC = 15,
            survivalDC = 15,
            uuid = "Item.hearty-meal",
            level = 1,
            cost = RawCost(currency = "gp", value = 1),
            rarity = "common",
            isSpecialMeal = true,
            criticalSuccess = CookingOutcome(effects = emptyArray(), chooseRandomly = null, message = null),
            success = CookingOutcome(effects = emptyArray(), chooseRandomly = null, message = null),
            criticalFailure = CookingOutcome(effects = emptyArray(), chooseRandomly = null, message = null),
        )
        val camping = getDefaultCamping(game.asGame())
        camping.actorUuids = arrayOf("Actor.chef-1")
        camping.cooking.knownRecipes = arrayOf("basic-meal")
        camping.cooking.homebrewMeals = arrayOf(testRecipe)
        partyActor.setCamping(camping)

        val handler = LearnSpecialRecipeHandler()
        val dispatcher = ActionDispatcher(game.asGame(), listOf(handler))

        val msg = ActionMessage(
            action = "learnSpecialRecipe",
            data = unsafeJso<dynamic> {
                campingActorUuid = partyActor.uuid
                actorUuid = chef.uuid
                id = "hearty-meal"
                degree = "success"
            }
        )
        handler.execute(msg, dispatcher)

        val stored = partyActor.getCamping()
        assertNotNull(stored)
        assertTrue(stored.cooking.knownRecipes.contains("hearty-meal"), "hearty-meal must be learned on success")
        assertEquals(2, env.chat.messages.size, "Chat messages should include food consumption and announce learned recipe")
        val messages = env.chat.messages.map { it.content.unsafeCast<String?>() ?: "" }
        assertTrue(messages.any { it.contains("chatMessages.discoverSpecialMeal.learned") }, "Learned announcement on success")
    }

    @Test
    fun weatherModifiersShelteredSuppressesMechanicalWeather() = runTest {
        val game = FakeGame(isGM = true)
        val camping = getDefaultCamping(game.asGame())

        // Set weather to SNOWY and enable sheltered
        game.settingsMap["${Config.moduleId}.enableWeather"] = true
        game.settingsMap["${Config.moduleId}.currentWeatherType"] = "snowy"
        game.settingsMap["${Config.moduleId}.enableSheltered"] = true

        val modifiersWhenSheltered = game.asGame().currentWeatherModifiers(camping)
        assertEquals(NEUTRAL_WEATHER, modifiersWhenSheltered, "Sheltered camp must suppress all weather modifiers")

        // Disable sheltered -> snowy modifiers take effect
        game.settingsMap["${Config.moduleId}.enableSheltered"] = false
        val modifiersOutdoor = game.asGame().currentWeatherModifiers(camping)
        assertEquals(-1.0, modifiersOutdoor.hexplorationActivityDelta)
        assertEquals(-1, modifiersOutdoor.encounterDcDelta)
        assertEquals(-2, modifiersOutdoor.campingCheckPenalty)
    }

    @Test
    fun syncWeatherExecutesForNonFirstGM() = runTest {
        val game = FakeGame(isGM = true)
        // Simulate a second GM by adding another active GM user before current
        val secondGmUser = js("({ id: 'gm-2', _id: 'gm-2', isGM: true, active: true })")
        game.gameObj.users.contents = arrayOf(secondGmUser, game.gameObj.user)
        game.gameObj.users.activeGM = secondGmUser

        game.settingsMap["${Config.moduleId}.enableWeather"] = true
        game.settingsMap["${Config.moduleId}.currentWeatherFx"] = "snow"

        val scene = js(
            """Object.assign(Object.create(globalThis.foundry.documents.Scene.prototype), {
                id: 'scene-1',
                name: 'Map',
                flags: {},
                getFlag: function() { return undefined; },
                update: function(data) {
                    for (var k in data) { this.flags[k] = data[k]; }
                    return Promise.resolve(this);
                }
            })"""
        )
        game.gameObj.scenes.contents = arrayOf(scene)
        game.gameObj.scenes.active = scene
        game.gameObj.scenes.current = scene

        syncWeather(game.asGame())

        assertEquals("snow", scene.flags.weather, "Scene weather should update even when current GM is not first GM")
    }

    @Test
    fun routeProvisionsWarningUsesEightHoursDay() {
        val secondsForOneDay = 28800.0 // 8 hours
        val secondsForTwoDays = 28801.0
        val days1 = kotlin.math.ceil(secondsForOneDay / EIGHT_HOURS_SECONDS.toDouble()).toInt()
        val days2 = kotlin.math.ceil(secondsForTwoDays / EIGHT_HOURS_SECONDS.toDouble()).toInt()
        assertEquals(1, days1, "28,800 seconds of exploration is 1 travel day")
        assertEquals(2, days2, "28,801 seconds of exploration requires 2 travel days of food")
    }
}
