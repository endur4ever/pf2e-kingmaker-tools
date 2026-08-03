package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.campaign.CampaignClock
import at.posselt.pfrpg2e.campaign.CampaignClockManager
import at.posselt.pfrpg2e.campaign.ClockTickEvent
import at.posselt.pfrpg2e.data.kingdom.applyStandingDelta
import at.posselt.pfrpg2e.data.kingdom.attitudeFor
import at.posselt.pfrpg2e.data.kingdom.shouldOfferDiplomacyQuest
import at.posselt.pfrpg2e.data.kingdom.shouldOfferWarThreat
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.RawModifier
import at.posselt.pfrpg2e.kingdom.data.endTurn
import at.posselt.pfrpg2e.kingdom.data.RawConsumption
import at.posselt.pfrpg2e.kingdom.RawCouncilCooldowns
import at.posselt.pfrpg2e.kingdom.data.RawFame
import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.data.RawResources
import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawWarPressure
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.kingdom.detectNewlyTriggeredThreats
import at.posselt.pfrpg2e.kingdom.WarThreatSnapshot

/** Convert [RawWarThreat] array to [WarThreatSnapshot] array for pure detection logic. */
private fun Array<RawWarThreat>.toThreatSnapshots(): Array<WarThreatSnapshot> =
    this.unsafeCast<Array<WarThreatSnapshot>>()

/** Persisted [RawArmyBattle.status] for battles archived at end of turn (not part of [BattleStatus]). */
const val ARCHIVED_BATTLE_STATUS = "archived"

/**
 * War-pressure modifiers applied at end turn.
 *
 * The war-pressure track computes [unrestModifier] (1 when pressure >= unrestThreshold)
 * and [consumptionModifier] (count of deployed armies). These are applied here in the
 * shared tick path so preview/commit parity is maintained.
 *
 * IMPORTANT: [consumptionModifier] equals the count of supporting armies, while
 * [RawConsumption.armies] tracks the *sum of each army's consumption value* (auto-calculated
 * from tokens via [ArmyConsumption.updateArmyConsumption]). They measure different things:
 * - consumptionModifier = number of deployed armies (war footing headcount)
 * - RawConsumption.armies = total food cost of those armies
 *
 * Both are applied. If auto-calculation is off, armies may be 0 while consumptionModifier > 0,
 * representing the logistical overhead of maintaining a war footing even without token data.
 */
private data class WarPressureModifiers(
    val unrestDelta: Int,
    val consumptionDelta: Int,
    val ruinThresholdCrossed: Boolean,
)

/** Compute war-pressure modifiers to apply this tick. Pure, no side effects. */
private fun computeWarPressureModifiers(
    newWarPressure: RawWarPressure?,
    previousWarPressure: RawWarPressure?,
): WarPressureModifiers {
    val pressure = newWarPressure ?: return WarPressureModifiers(0, 0, false)
    val prev = previousWarPressure

    val unrestDelta = pressure.unrestModifier
    val consumptionDelta = pressure.consumptionModifier

    // Ruin threshold: crossed this tick if pressure >= ruinThreshold now but was < ruinThreshold before
    val ruinThresholdCrossed = pressure.currentPressure >= pressure.ruinThreshold &&
        (prev?.currentPressure ?: 0) < (prev?.ruinThreshold ?: pressure.ruinThreshold)

    return WarPressureModifiers(unrestDelta, consumptionDelta, ruinThresholdCrossed)
}

/** Default number of turns to extend a quest deadline when GM clicks [Extend]. */
const val DEFAULT_QUEST_EXTEND_TURNS = 2

/**
 * Represents the diff of a single tick operation for auditing/logging.
 */
data class TickChange(
	val category: String,
	val field: String,
	val oldValue: Any?,
	val newValue: Any?,
)

/**
 * Engine output: all state changes produced by a kingdom turn tick.
 * Each field holds the post-tick value to apply to KingdomData.
 */
data class TickResult(
	val supernaturalSolutions: Int,
	val creativeSolutions: Int,
	val fame: RawFame,
	val resourcePoints: RawResources,
	val resourceDice: RawResources,
	val consumption: RawConsumption,
	val commodities: RawCurrentCommodities,
	val councilCooldowns: RawCouncilCooldowns?,
	val activityUsage: Array<RawActivityBlock> = emptyArray(),
	val modifiers: Array<RawModifier>,
	val changes: List<TickChange>,
	val clockEvents: Array<ClockTickEvent> = emptyArray(),
	val updatedClocks: Array<CampaignClock> = emptyArray(),
	val totalUnrestChange: Int = 0,

	/** War pressure crossed its ruin threshold this tick — performEndTurn posts the GM-confirmed ruin offer. */
	val ruinThresholdCrossed: Boolean = false,
	val campaignQuests: Array<dynamic> = emptyArray(),
		val warThreats: Array<RawWarThreat> = emptyArray(),
		val armyDeployments: Array<RawArmyDeployment> = emptyArray(),
		val warPressure: RawWarPressure? = null,
	val xpAwarded: Int = 0,
	val bonusResourceDice: Int = 0,
	val activeBattles: Array<RawArmyBattle> = emptyArray(),
	val groups: Array<RawGroup> = emptyArray(),
	val factionStandingDrift: Boolean = false,
	val warThreatOffers: Int = 0,
	val diplomacyQuestOffers: Int = 0,
	val questDeadlineReached: List<String> = emptyList(),
	/** Threats that were newly triggered (arrived) this tick, for GM offer cards. */
	val newlyTriggeredThreats: Array<RawWarThreat> = emptyArray(),
)

/**
 * Monthly (kingdom-turn) ticking engine.
 *
 * Processes the state transitions that occur once per kingdom turn — i.e. at the
 * **End Turn** action — which in PF2e Kingmaker is one calendar month:
 * - Resets one-shot solution counters
 * - Advances fame, resource points, resource dice, consumption
 * - Merges commodities with storage limits
 * - Counts down council cooldowns
 * - Ticks down modifier durations and expires finished modifiers
 * - Ticks campaign clocks
 * - Applies faction standing drift and evaluates threshold hooks (war threat / diplomacy quest offers)
 *
 * Day-scale concerns (weather, companion travel) are NOT handled here; they tick
 * daily off the world clock — see [DailyTickEngine] and `registerDailyTickHooks`.
 *
 * The engine contains no Foundry/Game dependencies, making it fully unit-testable.
 */
object TurnTickingEngine {

	/**
	 * Run a single kingdom-turn [tick] against the provided kingdom state snapshots.
	 *
	 * @param fame Current fame state (now/next).
	 * @param resourcePoints Current resource point state.
	 * @param resourceDice Current resource dice state.
	 * @param consumption Current consumption state.
	 * @param commodities Current commodity state.
	 * @param storage Commodity storage capacity (used to cap end-turn merge).
	 * @param councilCooldowns Nullable council cooldown state.
	 * @param warPressure Current war pressure state.
	 * @param currentTurn Current turn number (for war-threat bookkeeping).
	 * @param xp Current kingdom XP.
	 * @param xpThreshold XP needed to reach the next kingdom level.
	 * @param rpNow Current resource points (now) — used for RP-to-XP conversion.
	 * @param rpToXpConversionRate How many RP convert to 1 XP (e.g. 10 means 10 RP = 1 XP). 0 disables.
	 * @param rpToXpConversionLimit Max RP that can be converted per turn. 0 means no limit.
	 * @param maximumFamePoints Maximum fame the kingdom can hold.
	 * @param bonusResourceDice Bonus resource dice granted by the GM this turn (e.g. from events). Applied during collection, then reset.
	 * @param activeBattles Current active army battles to archive at end of turn.
	 * @param groups Current faction/group list with standing and treaty state.
	 * @param factionStandingDriftPerTurn Signed standing delta applied to every faction each turn (e.g. -1 for slow decay). 0 disables.
	 * @return [TickResult] with all post-tick values and a list of changes.
	 */
	fun tick(
		fame: RawFame,
		resourcePoints: RawResources,
		resourceDice: RawResources,
		consumption: RawConsumption,
		commodities: RawCurrentCommodities,
		storage: CommodityStorage,
		councilCooldowns: RawCouncilCooldowns?,
		activityUsage: Array<RawActivityBlock> = emptyArray(),
		modifiers: Array<RawModifier>,
		campaignClocks: Array<CampaignClock> = emptyArray(),
		campaignQuests: Array<dynamic> = emptyArray(),
		kingdomLevel: Int = 1,
		warThreats: Array<RawWarThreat> = emptyArray(),
		armyDeployments: Array<RawArmyDeployment> = emptyArray(),
		warPressure: RawWarPressure? = null,
		currentTurn: Int = 0,
		xp: Int = 0,
		xpThreshold: Int = 0,
		rpNow: Int = 0,
		rpToXpConversionRate: Int = 0,
		rpToXpConversionLimit: Int = 0,
		maximumFamePoints: Int = 3,
		autoGainFamePerTurn: Boolean = false,
		bonusResourceDice: Int = 0,
		activeBattles: Array<RawArmyBattle> = emptyArray(),
		groups: Array<RawGroup> = emptyArray(),
		factionStandingDriftPerTurn: Int = 0,
	): TickResult {
		val changes = mutableListOf<TickChange>()

		// 1) Reset solution counters
		changes += TickChange("solutions", "supernaturalSolutions", null, 0)
		changes += TickChange("solutions", "creativeSolutions", null, 0)

		// 2) Advance fame: next -> now, next reset to 0
		val newFame = RawFame(
			now = fame.next,
			next = 0,
			type = fame.type,
		)
		if (newFame.now != fame.now) {
			changes += TickChange("fame", "now", fame.now, newFame.now)
		}
		if (newFame.next != fame.next) {
			changes += TickChange("fame", "next", fame.next, newFame.next)
		}

		// 3) Advance resource points: next -> now
		val tributeRp = groups.filter { it.allianceLevel == "tribute" }.sumOf { 2 }
		val newResourcePoints = if (tributeRp > 0) {
			RawResources(
				now = resourcePoints.next + tributeRp,
				next = 0
			)
		} else {
			resourcePoints.endTurn()
		}
		if (newResourcePoints.now != resourcePoints.now) {
			changes += TickChange("resourcePoints", "now", resourcePoints.now, newResourcePoints.now)
		}
		if (newResourcePoints.next != resourcePoints.next) {
			changes += TickChange("resourcePoints", "next", resourcePoints.next, newResourcePoints.next)
		}
		if (tributeRp > 0) {
			changes += TickChange("resourcePoints", "tribute", 0, tributeRp)
		}

		// 4) Advance resource dice: next -> now
		val newResourceDice = resourceDice.endTurn()
		if (newResourceDice.now != resourceDice.now) {
			changes += TickChange("resourceDice", "now", resourceDice.now, newResourceDice.now)
		}
		if (newResourceDice.next != resourceDice.next) {
			changes += TickChange("resourceDice", "next", resourceDice.next, newResourceDice.next)
		}

		// 5) Advance consumption: next -> now
		var newConsumption = consumption.endTurn()
		if (newConsumption.now != consumption.now) {
			changes += TickChange("consumption", "now", consumption.now, newConsumption.now)
		}

		// 6) Merge commodities with storage cap
		val newCommodities = commodities.endTurn(storage)

		// 7) Tick down council cooldowns
		val newCooldowns = if (councilCooldowns != null) {
			val updated = RawCouncilCooldowns(
				audit = (councilCooldowns.audit - 1).coerceAtLeast(0),
				scrying = (councilCooldowns.scrying - 1).coerceAtLeast(0),
				lockdown = (councilCooldowns.lockdown - 1).coerceAtLeast(0),
				feast = (councilCooldowns.feast - 1).coerceAtLeast(0),
			)
			if (updated.audit != councilCooldowns.audit) {
				changes += TickChange("councilCooldowns", "audit", councilCooldowns.audit, updated.audit)
			}
			if (updated.scrying != councilCooldowns.scrying) {
				changes += TickChange("councilCooldowns", "scrying", councilCooldowns.scrying, updated.scrying)
			}
			if (updated.lockdown != councilCooldowns.lockdown) {
				changes += TickChange("councilCooldowns", "lockdown", councilCooldowns.lockdown, updated.lockdown)
			}
			if (updated.feast != councilCooldowns.feast) {
				changes += TickChange("councilCooldowns", "feast", councilCooldowns.feast, updated.feast)
			}
			updated
		} else {
			null
		}

		// 7.5) Settle escalating-DC activity usage (+2 if used this turn, else −1; drop idle entries).
		// Timeout lockouts self-expire against currentTurn, so no per-turn decrement is needed here.
		val newActivityUsage = tickActivityUsages(activityUsage.toActivityUsages(), currentTurn)
			.toRawActivityBlocks()

		// 8) Tick down modifier durations
		var expiredCount = 0
		val newModifiers = modifiers.mapNotNull { mod ->
			val turns = mod.turns
			if (turns == null || turns == 0) {
				// Permanent modifier, keep as-is
				mod
			} else if (turns <= 1) {
				// Expired after this tick
				expiredCount++
				null
			} else {
				// Decrement remaining turns
				RawModifier(
					id = mod.id,
					type = mod.type,
					value = mod.value,
					name = mod.name,
					enabled = mod.enabled,
					turns = turns - 1,
					buttonLabel = mod.buttonLabel,
					valueExpression = mod.valueExpression,
					isConsumedAfterRoll = mod.isConsumedAfterRoll,
					rollOptions = mod.rollOptions,
					applyIf = mod.applyIf,
					fortune = mod.fortune,
					rollTwiceKeepLowest = mod.rollTwiceKeepLowest,
					rollTwiceKeepHighest = mod.rollTwiceKeepHighest,
					upgradeResults = mod.upgradeResults,
					downgradeResults = mod.downgradeResults,
					notes = mod.notes,
					requiresTranslation = mod.requiresTranslation,
					selector = mod.selector,
				)
			}
		}.toTypedArray()

		if (expiredCount > 0) {
			changes += TickChange("modifiers", "expired", null, expiredCount)
		}

		// 9) Tick campaign clocks
		val clockResult = CampaignClockManager.tickAll(campaignClocks)

		// 10) Tick quest timers
		val (updatedQuests, deadlineReached, questChanges) = tickQuests(campaignQuests, kingdomLevel)
		changes += questChanges

		// Track offer counters early so war-pressure ruin threshold can increment them.
		var warThreatOffers = 0
		var diplomacyQuestOffers = 0

		// 11) Tick war threats (roadmap #12): ETA countdown, escalation, expiry/soft-pause
		val tickedThreats = tickWarThreats(warThreats, currentTurn)

		// Detect newly triggered threats (arrived this tick) for GM offer cards
		val newlyTriggeredSnapshots = detectNewlyTriggeredThreats(
			warThreats.toThreatSnapshots(),
			tickedThreats.toThreatSnapshots(),
			currentTurn
		)
		val newlyTriggeredIds = newlyTriggeredSnapshots.map { it.id }.toSet()
		val newlyTriggered = tickedThreats.filter { it.id in newlyTriggeredIds }

		// 12) Recalculate war pressure from active threats minus supporting armies
		val newWarPressure = if (warThreats.isNotEmpty() || armyDeployments.isNotEmpty() || warPressure != null) {
			recalculateWarPressure(tickedThreats, armyDeployments, warPressure)
		} else {
			warPressure
		}
		if (newWarPressure != null && (newWarPressure.lastChange ?: 0) != 0) {
			changes += TickChange("warPressure", "currentPressure", warPressure?.currentPressure, newWarPressure.currentPressure)
		}

		// 12b) Apply war-pressure modifiers (unrest/consumption/ruin) in the shared tick path
		// so preview/commit parity is maintained.
		val wpModifiers = computeWarPressureModifiers(newWarPressure, warPressure)
		var totalUnrestChange = clockResult.totalUnrestChange
		if (wpModifiers.unrestDelta > 0) {
			totalUnrestChange += wpModifiers.unrestDelta
			changes += TickChange("unrest", "warPressure", null, wpModifiers.unrestDelta)
		}
		// Apply consumption modifier to the current turn's consumption (consumption.now was
		// already set to the previous consumption.next by endTurn() in step 5).
		// Note: consumptionModifier tracks deployed army COUNT (war footing overhead),
		// while RawConsumption.armies tracks the sum of each army's consumption value.
		// Both are applied; they represent different cost categories.
		if (wpModifiers.consumptionDelta > 0) {
			newConsumption = RawConsumption.copy(newConsumption, now = newConsumption.now + wpModifiers.consumptionDelta)
			changes += TickChange("consumption", "warPressure", null, wpModifiers.consumptionDelta)
		}
		// Ruin threshold crossed: surfaced on the TickResult so performEndTurn posts the
		// ruin-specific GM-confirmed offer card. (It previously bumped warThreatOffers — a
		// counter no consumer reads — so the promised offer silently vanished.)

		// 13) RP-to-XP conversion: convert current RP into XP based on rate and limit
		var xpAwarded = 0
		if (rpToXpConversionRate > 0 && rpNow > 0) {
			val convertibleRp = if (rpToXpConversionLimit > 0) {
				minOf(rpNow, rpToXpConversionLimit)
			} else {
				rpNow
			}
			xpAwarded = convertibleRp / rpToXpConversionRate
			if (xpAwarded > 0) {
				changes += TickChange("xp", "xpAwarded", null, xpAwarded)
			}
		}

		// 14) Auto-gain fame per turn (up to maximumFamePoints)
		val fameAfterAutoGain = if (autoGainFamePerTurn) {
			val currentFameNow = newFame.now
			if (currentFameNow < maximumFamePoints) {
				val withAutoFame = RawFame(now = currentFameNow + 1, next = newFame.next, type = newFame.type)
				changes += TickChange("fame", "autoGain", currentFameNow, withAutoFame.now)
				withAutoFame
			} else {
				newFame
			}
		} else {
			newFame
		}

		// 15) Reset bonus resource dice after applying them
		if (bonusResourceDice != 0) {
			changes += TickChange("bonusResourceDice", "reset", bonusResourceDice, 0)
		}

		// 16) Archive finished battles: any battle no longer ACTIVE (victory,
		// defeat, retreat, …) is marked archived so the board only offers
		// live battles for resolution. Already-archived ones pass through.
		val archivedBattles = activeBattles.map { battle ->
			if (battle.status != BattleStatus.ACTIVE.value && battle.status != ARCHIVED_BATTLE_STATUS) {
				changes += TickChange("battle", "archived", battle.id, battle.status)
				RawArmyBattle.copy(battle, status = ARCHIVED_BATTLE_STATUS)
			} else {
				battle
			}
		}.toTypedArray()

		// 17) Faction standing drift — applies a signed delta to every group's
		// standing once per turn, then checks attitude threshold crossings to
		// determine whether a war-threat or diplomacy-quest hook should fire.
		val driftedGroups = if (factionStandingDriftPerTurn != 0 && groups.isNotEmpty()) {
			groups.map { group ->
				val before = group.standing
				val after = applyStandingDelta(before, factionStandingDriftPerTurn)
				if (before != after) {
					changes += TickChange(
						category = "factionStanding",
						field = group.name,
						oldValue = before,
						newValue = after,
					)
					val logEntry = RawFactionStandingEntry(
						turn = currentTurn,
						delta = factionStandingDriftPerTurn,
						reason = "kingdom.factionStanding.drift",
					)
					val newLog = if (group.standingLog != null) {
						group.standingLog!! + logEntry
					} else {
						arrayOf(logEntry)
					}
					val drifted = RawGroup.copy(group, standing = after, standingLog = newLog)
					if (shouldOfferWarThreat(before, after)) warThreatOffers++
					if (shouldOfferDiplomacyQuest(before, after)) diplomacyQuestOffers++
					drifted
				} else {
					group
				}
			}.toTypedArray()
		} else {
			groups
		}

		return TickResult(
					supernaturalSolutions = 0,
					creativeSolutions = 0,
					fame = fameAfterAutoGain,
					resourcePoints = newResourcePoints,
					resourceDice = newResourceDice,
					consumption = newConsumption,
					commodities = newCommodities,
					councilCooldowns = newCooldowns,
					activityUsage = newActivityUsage,
					modifiers = newModifiers,
					changes = changes,
					clockEvents = clockResult.events,
					updatedClocks = clockResult.updatedClocks,
					totalUnrestChange = totalUnrestChange,
					campaignQuests = updatedQuests,
					warThreats = tickedThreats,
					armyDeployments = armyDeployments,
					warPressure = newWarPressure,
					xpAwarded = xpAwarded,
					bonusResourceDice = 0,
					activeBattles = archivedBattles,
					groups = driftedGroups,
					factionStandingDrift = factionStandingDriftPerTurn != 0,
					warThreatOffers = warThreatOffers,
					diplomacyQuestOffers = diplomacyQuestOffers,
					questDeadlineReached = deadlineReached,
					newlyTriggeredThreats = newlyTriggered.toTypedArray(),
					ruinThresholdCrossed = wpModifiers.ruinThresholdCrossed,
				)
	}

	/**
	 * Advance quest timers for generated quests.
	 * Decrements turnsRemaining on ACTIVE generated quests; marks as deadlineReached at 0.
	 * Returns the updated quest array, quest IDs that hit 0 this tick, and any TickChange entries produced.
	 */
	fun tickQuests(
		quests: Array<dynamic>,
		kingdomLevel: Int,
	): Triple<Array<dynamic>, List<String>, List<TickChange>> {
		val changes = mutableListOf<TickChange>()
		val deadlineReached = mutableListOf<String>()
		val updated = quests.map { quest ->
			val status = quest.status as? String
			val generated = quest.generatedByEvent as? Boolean ?: false
			val turns = quest.turnsRemaining as? Int
			val questId = quest.id as? String ?: ""
			if (status == "active" && generated && turns != null && turns > 0) {
				val newTurns = turns - 1
				if (newTurns <= 0) {
					// Quest deadline reached — stays active at 0 turns, offer will be posted by GM
					changes += TickChange("quest", "turnsRemaining", turns, 0)
					quest.turnsRemaining = 0
					// Track that this quest hit 0 this tick (for GM offer)
					deadlineReached.add(questId)
					quest
				} else {
					changes += TickChange("quest", "turnsRemaining", turns, newTurns)
					quest.turnsRemaining = newTurns
					quest
				}
			} else {
				quest
			}
		}.toTypedArray()
		return Triple(updated, deadlineReached, changes)
	}
}
