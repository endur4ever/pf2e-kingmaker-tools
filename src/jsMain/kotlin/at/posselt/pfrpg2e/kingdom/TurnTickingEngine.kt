package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.campaign.CampaignClock
import at.posselt.pfrpg2e.campaign.CampaignClockManager
import at.posselt.pfrpg2e.campaign.ClockTickEvent
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.kingdom.data.RawCurrentCommodities
import at.posselt.pfrpg2e.kingdom.RawModifier
import at.posselt.pfrpg2e.kingdom.data.endTurn
import at.posselt.pfrpg2e.kingdom.data.RawConsumption
import at.posselt.pfrpg2e.kingdom.RawCouncilCooldowns
import at.posselt.pfrpg2e.kingdom.data.RawFame
import at.posselt.pfrpg2e.kingdom.data.RawResources
import at.posselt.pfrpg2e.kingdom.data.RawArmyDeployment
import at.posselt.pfrpg2e.kingdom.data.RawWarPressure
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat

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
	val modifiers: Array<RawModifier>,
	val changes: List<TickChange>,
	val clockEvents: Array<ClockTickEvent> = emptyArray(),
	val updatedClocks: Array<CampaignClock> = emptyArray(),
	val totalUnrestChange: Int = 0,
	val campaignQuests: Array<dynamic> = emptyArray(),
		val warThreats: Array<RawWarThreat> = emptyArray(),
		val armyDeployments: Array<RawArmyDeployment> = emptyArray(),
		val warPressure: RawWarPressure? = null,
	val xpAwarded: Int = 0,
	val bonusResourceDice: Int = 0,
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
	 * @param autoGainFamePerTurn If true, automatically gain 1 fame at end of turn (up to maximum).
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
		val newResourcePoints = resourcePoints.endTurn()
		if (newResourcePoints.now != resourcePoints.now) {
			changes += TickChange("resourcePoints", "now", resourcePoints.now, newResourcePoints.now)
		}
		if (newResourcePoints.next != resourcePoints.next) {
			changes += TickChange("resourcePoints", "next", resourcePoints.next, newResourcePoints.next)
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
		val newConsumption = consumption.endTurn()
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
		val (updatedQuests, questChanges) = tickQuests(campaignQuests, kingdomLevel)
		changes += questChanges

		// 11) Tick war threats (roadmap #12): ETA countdown, escalation, expiry/soft-pause
		val tickedThreats = tickWarThreats(warThreats, currentTurn)

		// 12) Recalculate war pressure from active threats minus supporting armies
		val newWarPressure = if (warThreats.isNotEmpty() || armyDeployments.isNotEmpty() || warPressure != null) {
			recalculateWarPressure(tickedThreats, armyDeployments, warPressure)
		} else {
			warPressure
		}
		if (newWarPressure != null && (newWarPressure.lastChange ?: 0) != 0) {
			changes += TickChange("warPressure", "currentPressure", warPressure?.currentPressure, newWarPressure.currentPressure)
		}

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

		return TickResult(
			supernaturalSolutions = 0,
			creativeSolutions = 0,
			fame = fameAfterAutoGain,
			resourcePoints = newResourcePoints,
			resourceDice = newResourceDice,
			consumption = newConsumption,
			commodities = newCommodities,
			councilCooldowns = newCooldowns,
			modifiers = newModifiers,
			changes = changes,
			clockEvents = clockResult.events,
			updatedClocks = clockResult.updatedClocks,
			totalUnrestChange = clockResult.totalUnrestChange,
			campaignQuests = updatedQuests,
			warThreats = tickedThreats,
			armyDeployments = armyDeployments,
			warPressure = newWarPressure,
			xpAwarded = xpAwarded,
			bonusResourceDice = 0,
		)
	}

	/**
	 * Advance quest timers for generated quests.
	 * Decrements turnsRemaining on ACTIVE generated quests; marks as FAILED at 0.
	 * Returns the updated quest array and any TickChange entries produced.
	 */
	fun tickQuests(
		quests: Array<dynamic>,
		kingdomLevel: Int,
	): Pair<Array<dynamic>, List<TickChange>> {
		val changes = mutableListOf<TickChange>()
		val updated = quests.map { quest ->
			val status = quest.status as? String
			val generated = quest.generatedByEvent as? Boolean ?: false
			val turns = quest.turnsRemaining as? Int
			if (status == "active" && generated && turns != null && turns > 0) {
				val newTurns = turns - 1
				if (newTurns <= 0) {
					changes += TickChange("quest", "status", "active", "failed")
					changes += TickChange("quest", "turnsRemaining", turns, 0)
					quest.asDynamic().status = "failed"
					quest.asDynamic().turnsRemaining = 0
					quest
				} else {
					changes += TickChange("quest", "turnsRemaining", turns, newTurns)
					quest.asDynamic().turnsRemaining = newTurns
					quest
				}
			} else {
				quest
			}
		}.toTypedArray()
		return Pair(updated, changes)
	}
}
