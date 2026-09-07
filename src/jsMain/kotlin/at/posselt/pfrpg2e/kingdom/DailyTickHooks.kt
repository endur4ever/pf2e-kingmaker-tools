package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.camping.tickRumorLifecycles
import at.posselt.pfrpg2e.camping.getActiveCamping
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
import at.posselt.pfrpg2e.kingdom.data.WarThreatStatus
import at.posselt.pfrpg2e.kingdom.data.toModel
import at.posselt.pfrpg2e.kingdom.pressure.ScheduledPressure
import at.posselt.pfrpg2e.kingdom.pressure.dueFirings
import at.posselt.pfrpg2e.kingdom.pressure.buildPressureDigestContext
import at.posselt.pfrpg2e.kingdom.pressure.isPressureResolved
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeProject
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeStatus
import at.posselt.pfrpg2e.kingdom.downtime.DOWNTIME_HISTORY_CAP
import at.posselt.pfrpg2e.camping.downtimeCompleteContext
import at.posselt.pfrpg2e.kingdom.downtime.prerequisiteMet
import at.posselt.pfrpg2e.kingdom.downtime.tickDowntimeProjects
import at.posselt.pfrpg2e.resting.DAY_SECONDS
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.escapeHtml
import at.posselt.pfrpg2e.utils.fromUuidOfTypes
import at.posselt.pfrpg2e.utils.isFirstGM
import at.posselt.pfrpg2e.utils.worldTimeSeconds
import at.posselt.pfrpg2e.utils.postChatMessage
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.typeSafeUpdate
import at.posselt.pfrpg2e.weather.rollWeather
import com.foundryvtt.core.Game
import com.foundryvtt.pf2e.actor.PF2ECharacter
import com.foundryvtt.pf2e.actor.PF2ENpc
import com.foundryvtt.core.grid.GridHex
import com.foundryvtt.core.grid.GridOffset2D
import com.foundryvtt.core.grid.HexagonalGrid
import com.foundryvtt.core.grid.HexagonalGridCube2D
import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.core.helpers.onUpdateWorldTime
import com.foundryvtt.core.helpers.Hooks
import at.posselt.pfrpg2e.data.regions.getMonth
import com.pixijs.Point
import js.objects.recordOf
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

/**
 * Registers the daily ticking hooks.
 *
 * Unlike the kingdom turn (monthly, driven by the End Turn button and
 * [TurnTickingEngine]), these are *day-scale* ticks driven automatically by the
 * Foundry world clock. They fire whenever world time crosses one or more calendar
 * day boundaries — which happens when the party rests in the camping sheet, uses
 * the Set Time of Day macro, or the GM advances the clock.
 *
 * On each new day this:
 * - Rolls the daily weather via `rollWeather` (same path as the manual macro:
 *   flat checks, weather events, scene FX, chat), gated by the weather setting and
 *   the camping "Check Weather" toggle.
 * - Moves traveling companions' tokens toward their destination hex and posts an
 *   arrival message when they get there.
 */
internal var lastTickedWorldDay: Int? = null

/**
 * Calculates how many *new* unticked days were crossed, taking into account a high-water mark
 * so rewinding and re-advancing the world clock does not re-trigger daily ticks.
 */
internal fun untickedDaysCrossed(worldTime: Int, deltaInSeconds: Int, highWaterMark: Int?): Pair<Int, Int?> {
	if (deltaInSeconds <= 0) return 0 to highWaterMark
	val daysPassed = daysCrossed(worldTime, deltaInSeconds)
	if (daysPassed <= 0) return 0 to highWaterMark
	val currentDay = worldTime.floorDiv(DAY_SECONDS)
	val previousDay = (worldTime - deltaInSeconds).floorDiv(DAY_SECONDS)
	val effectiveHighWater = highWaterMark ?: previousDay
	val newDays = (currentDay - effectiveHighWater).coerceAtLeast(0)
	val daysToTick = minOf(daysPassed, newDays)
	val newHighWater = if (daysToTick > 0) maxOf(effectiveHighWater, currentDay) else highWaterMark
	return daysToTick to newHighWater
}

fun registerDailyTickHooks(game: Game) {
	if (lastTickedWorldDay == null) {
		lastTickedWorldDay = game.time.worldTimeSeconds.floorDiv(DAY_SECONDS)
	}
	TypedHooks.onUpdateWorldTime { worldTime, deltaInSeconds, _, _ ->
		val (daysPassed, newHighWater) = untickedDaysCrossed(worldTime, deltaInSeconds, lastTickedWorldDay)
		lastTickedWorldDay = newHighWater
		if (game.isFirstGM() && daysPassed >= 1) {
			buildPromise {
					rollDailyWeather(game)
					tickCompanionTravel(game, daysPassed)
					tickCompanionExpeditions(game, daysPassed)
					tickPersonalQuests(game, daysPassed)
					tickPcDowntimeProjects(game, daysPassed)
					tickScheduledPressures(game, worldTime, daysPassed)
					tickRumorLifecycles(game, daysPassed)
				}
		}
	}

	Hooks.on("simple-calendar-date-time-change") { data: dynamic ->
		if (game.isFirstGM() && data != null && data.newDate != null && data.previousDate != null) {
			val newMonth = data.newDate.month.unsafeCast<Int>()
			val prevMonth = data.previousDate.month.unsafeCast<Int>()
			if (newMonth != prevMonth) {
				game.getKingdomActors().forEach { actor ->
					val kingdom = actor.getKingdom() ?: return@forEach
					if (kingdom.settings.enableCalendarMonthEndTurn == true) {
						buildPromise {
							postChatMessage(
								t("kingdom.calendarMonthEndTurnPrompt", recordOf("month" to t(getMonth(newMonth)))),
								isHtml = true
							)
						}
					}
				}
			}
		}
	}
}

/**
 * Number of calendar-day boundaries crossed by advancing from
 * (`worldTime` - `deltaInSeconds`) to `worldTime`. Zero or negative when time did
 * not advance past a day boundary (or was rewound).
 */
internal fun daysCrossed(worldTime: Int, deltaInSeconds: Int): Int {
	if (deltaInSeconds <= 0) return 0
	val previous = worldTime - deltaInSeconds
	return worldTime.floorDiv(DAY_SECONDS) - previous.floorDiv(DAY_SECONDS)
}

private suspend fun rollDailyWeather(game: Game) {
	val weatherEnabled = game.settings.pfrpg2eKingdomCampingWeather.getEnableWeather()
	// Honor the camping sheet's per-rest "Check Weather" toggle when a camp is active.
	val camping = game.getActiveCamping()
	val skipWeather = camping != null && camping.restSettings.skipWeather
	if (weatherEnabled && !skipWeather) {
		rollWeather(game)
	}
}

private suspend fun tickCompanionTravel(game: Game, daysPassed: Int) {
	game.getKingdomActors().forEach { actor ->
		val kingdom = actor.getKingdom() ?: return@forEach
		val companions = kingdom.companions ?: return@forEach
		var changed = false
		for (companion in companions) {
			// En route = active with a destination set. Movement is gradual: the token
			// steps toward the destination each day rather than teleporting on arrival.
			if (!companion.active) continue
			if (companion.destinationX == null || companion.destinationY == null) continue
			val arrived = advanceCompanionTravel(game, companion, daysPassed)
			changed = true
			companion.traveling = !arrived
			if (arrived) {
				announceArrival(game, companion)
				// Clear the trip so it doesn't keep "arriving" on subsequent days.
				companion.destinationX = null
				companion.destinationY = null
				companion.eta = null
			}
		}
		if (changed) {
			kingdom.companions = companions
			actor.setKingdom(kingdom)
		}
	}
}

/**
 * Advance one companion's token toward its destination hex by [daysPassed] days.
 *
 * Rate:
 *  - ETA set (>= 1): paced so it arrives in exactly that many days.
 *  - ETA blank: `speed` hexes per day.
 *
 * Returns true if the token reached the destination on this tick. Mutates
 * [RawCharacter.eta] as it counts down. Falls back to an ETA-only countdown (so the
 * arrival chat still fires) when there is no token to move on the active hex scene.
 */
private suspend fun advanceCompanionTravel(game: Game, companion: RawCharacter, daysPassed: Int): Boolean {
	val destX = companion.destinationX ?: return false
	val destY = companion.destinationY ?: return false

	val activeScene = game.scenes.active
	val uuid = companion.actorUuid
	val companionActor = uuid?.let { fromUuidOfTypes(it, PF2ECharacter::class, PF2ENpc::class) }
	val tokenDoc = if (activeScene != null && activeScene.grid.isHexagonal && companionActor != null) {
		activeScene.tokens.contents.find { it.actorId == companionActor.id }
	} else {
		null
	}

	if (activeScene == null || tokenDoc == null) {
		// No token to move -> ETA-only countdown so the arrival chat still fires.
		val eta = companion.eta ?: return false
		val newEta = eta - daysPassed
		companion.eta = if (newEta <= 0) null else newEta
		return newEta <= 0
	}

	val grid = activeScene.grid
	val hexGrid = grid.unsafeCast<HexagonalGrid>()
	val centerPoint = Point(
		x = tokenDoc.x + grid.sizeX / 2.0,
		y = tokenDoc.y + grid.sizeY / 2.0,
	)
	val currentOffset = grid.getOffset(centerPoint)
	// Foundry offsets are { i = row (Y), j = column (X) }.
	val destOffset = GridOffset2D(i = destY, j = destX)
	val currentCube = GridHex(currentOffset, hexGrid).cube
	val destCube = GridHex(destOffset, hexGrid).cube
	val distance = cubeDistance(currentCube, destCube)

	if (distance <= 0) {
		companion.eta = null
		return true
	}

	val eta = companion.eta
	val stepHexes: Int
	val arrivedByEta: Boolean
	if (eta != null && eta >= 1) {
		// Pace so the remaining distance is covered in exactly `eta` more days.
		stepHexes = ceil(distance.toDouble() * daysPassed / eta).toInt().coerceAtLeast(1)
		val newEta = eta - daysPassed
		companion.eta = if (newEta <= 0) null else newEta
		arrivedByEta = newEta <= 0
	} else {
		stepHexes = companion.speed.coerceAtLeast(0) * daysPassed
		companion.eta = null
		arrivedByEta = false
		if (stepHexes <= 0) {
			return false
		}
	}

	val arrived = arrivedByEta || stepHexes >= distance
	val targetCube = if (arrived) destCube else cubeLerpStep(currentCube, destCube, stepHexes, distance)
	val point = GridHex(targetCube, hexGrid).topLeft
	tokenDoc.typeSafeUpdate {
		x = point.x
		y = point.y
	}
	return arrived
}

/**
 * Daily tick for companion expeditions: counts down active expeditions,
 * triggers resolution when complete, and decrements injury timers.
 *
 * Mirrors [tickCompanionTravel] structure: iterate kingdom actors, read
 * `companionExpeditions`, apply the pure [DailyTickEngine.tickExpedition]
 * countdown, and when a crossing fires:
 *   - set `status = 'awaitingResolution'`
 *   - call [offerExpeditionResolution] (impure wrapper: roll + accrue + persist)
 *
 * The same loop decrements `injuryDaysRemaining`; at <= 0 clears the injury,
 * restores `expeditionStatus` to `available` and `campAvailable` to true,
 * and posts an escaped "X recovered" line.
 *
 * `game.time.advance` is NOT called here ([DailyTickEngine] is pure).
 */
private suspend fun tickCompanionExpeditions(game: Game, daysPassed: Int) {
	game.getKingdomActors().forEach { actor ->
		val kingdom = actor.getKingdom() ?: return@forEach
		val companions = kingdom.companions ?: return@forEach
		val expeditions = kingdom.companionExpeditions ?: return@forEach
		if (expeditions.isEmpty()) return@forEach

		var changed = false
		// Work on a mutable copy so we can reassign atomically.
		val updated = expeditions.toMutableList()

		for (i in updated.indices) {
			val exp = updated[i]
			if (exp.status != "inProgress") continue

			val result = DailyTickEngine.tickExpedition(exp.daysRemaining, daysPassed)
			exp.daysRemaining = result.newDaysRemaining
			changed = true

			if (result.completed) {
				exp.status = "awaitingResolution"
				// Find the companion(s) for this expedition to resolve the check.
				for (companion in companions) {
					val isParticipant = exp.companionIds.any { id ->
						id == companion.actorUuid || id == companion.name
					}
					if (isParticipant) {
						offerExpeditionResolution(game, actor, companion, exp, kingdom)
						break  // one resolution per expedition
					}
				}
			}
		}

		// Decrement injury timers on companions not already on expedition.
		for (companion in companions) {
			val injuryDays = companion.injuryDaysRemaining ?: continue
			val remaining = injuryDays - daysPassed
			if (remaining <= 0) {
				companion.injuryDaysRemaining = null
				companion.expeditionStatus = "available"
				companion.campAvailable = true
				changed = true
				val name = escapeHtml(
					companion.actorUuid?.let {
						fromUuidOfTypes(it, PF2ECharacter::class, PF2ENpc::class)?.name
					} ?: companion.name
				)
				postChatMessage(
					t("kingdom.companionRecovered", recordOf("name" to name)),
					isHtml = true,
				)
			} else {
				companion.injuryDaysRemaining = remaining
				changed = true
			}
		}

		if (changed) {
			kingdom.companionExpeditions = updated.toTypedArray()
			actor.setKingdom(kingdom)
		}
	}
}

/**
 * Daily tick for companion personal quests: decrements [CompanionPersonalQuest.turnsRemaining]
 * for each active quest with a deadline. When [turnsRemaining] reaches zero the quest is marked
 * as "failed" (the companion ran out of time).
 *
 * Travels with [tickCompanionExpeditions] since both are day-scale ticks driven by the
 * world-clock hook.
 */
private suspend fun tickPersonalQuests(game: Game, daysPassed: Int) {
	game.getKingdomActors().forEach { actor ->
		val kingdom = actor.getKingdom() ?: return@forEach
		val quests = kingdom.companionPersonalQuests ?: return@forEach
		if (quests.isEmpty()) return@forEach

		var changed = false
		val updated = quests.toMutableList()
		for (i in updated.indices) {
			val quest = updated[i]
			val result = DailyTickEngine.tickPersonalQuest(quest.status, quest.turnsRemaining, daysPassed)
			if (result.newStatus != quest.status || result.newTurnsRemaining != quest.turnsRemaining) {
				quest.status = result.newStatus
				quest.turnsRemaining = result.newTurnsRemaining
				changed = true
				if (result.failed) {
					val companionName = kingdom.companions
						?.find { (it.actorUuid ?: it.name) == quest.companionId }
						?.name
						?: quest.companionId
					postChatMessage(
						t(
							"kingdom.companionQuestFailed",
							recordOf("quest" to escapeHtml(quest.title), "name" to escapeHtml(companionName)),
						),
						isHtml = true,
					)
				}
			}
		}
		if (changed) {
			kingdom.companionPersonalQuests = updated.toTypedArray()
			actor.setKingdom(kingdom)
		}
	}
}

private suspend fun announceArrival(game: Game, companion: RawCharacter) {
	val companionName = companion.actorUuid
		?.let { fromUuidOfTypes(it, PF2ECharacter::class, PF2ENpc::class)?.name }
		?: companion.name
	val destX = companion.destinationX ?: 0
	val destY = companion.destinationY ?: 0
	postChatMessage(
		// The message template is HTML; the actor-provided name must not inject into it.
		t("kingdom.companionArrival", recordOf("name" to escapeHtml(companionName), "x" to destX, "y" to destY)),
		isHtml = true,
	)
	logToCalendar(
		title = "Companion Arrival",
		content = "$companionName has arrived at hex ($destX, $destY)."
	)
}

/** Hex distance between two cube coordinates. */
private fun cubeDistance(a: HexagonalGridCube2D, b: HexagonalGridCube2D): Int =
	(abs(a.q - b.q) + abs(a.r - b.r) + abs(a.s - b.s)) / 2

/** The cube coordinate [steps] hexes along the straight line from [from] to [to]. */
private fun cubeLerpStep(
	from: HexagonalGridCube2D,
	to: HexagonalGridCube2D,
	steps: Int,
	distance: Int,
): HexagonalGridCube2D {
	if (distance <= 0) return to
	val t = (steps.toDouble() / distance.toDouble()).coerceIn(0.0, 1.0)
	return cubeRound(
		from.q + (to.q - from.q) * t,
		from.r + (to.r - from.r) * t,
		from.s + (to.s - from.s) * t,
	)
}

/** Round fractional cube coordinates to the nearest valid hex (q + r + s == 0). */
private fun cubeRound(q: Double, r: Double, s: Double): HexagonalGridCube2D {
	var rq = round(q)
	var rr = round(r)
	var rs = round(s)
	val dq = abs(rq - q)
	val dr = abs(rr - r)
	val ds = abs(rs - s)
	if (dq > dr && dq > ds) {
		rq = -rr - rs
	} else if (dr > ds) {
		rr = -rq - rs
	} else {
		rs = -rq - rr
	}
	return HexagonalGridCube2D(q = rq.toInt(), r = rr.toInt(), s = rs.toInt())
}

/**
 * Ticks PC downtime projects by the number of day boundaries crossed
 * (`docs/plans/2026-07-09-plan-downtime-projects.md` §6, phase 2).
 *
 * Rows whose stored kind or status this build does not recognise are left UNTOUCHED in storage —
 * they are skipped by the model mapping, never deleted, so a newer build's projects survive a
 * round-trip through an older one. Eligibility re-checks the hosting settlement's structures every
 * tick: a razed smithy pauses the craft (via the pure engine) instead of letting it complete
 * somewhere that no longer exists. Phase 2 is state-only by design — the completion offer card is
 * phase 4, so a completed project simply rests at completed until then.
 */
private suspend fun tickPcDowntimeProjects(game: Game, daysPassed: Int) {
	game.getKingdomActors().forEach { actor ->
		val kingdom = actor.getKingdom() ?: return@forEach
		val raws = kingdom.downtimeProjects ?: return@forEach
		if (raws.isEmpty()) return@forEach
		val models = raws.mapNotNull { it.toModel() }
		if (models.none { it.status == DowntimeStatus.IN_PROGRESS }) return@forEach

		// Settlements are only resolved when some in-progress project actually names one.
		val settlements = if (models.any { it.status == DowntimeStatus.IN_PROGRESS && it.settlementId != null }) {
			runCatching { kingdom.getAllSettlements(game).allSettlements }.getOrDefault(emptyList())
		} else {
			emptyList()
		}
		val stillEligible: (DowntimeProject) -> Boolean = eligible@{ project ->
			val settlementId = project.settlementId ?: return@eligible true
			val settlement = settlements.find { it.id == settlementId } ?: return@eligible false
			prerequisiteMet(project.kind, settlement.constructedStructures.map { it.name }.toSet())
		}
		val outcome = tickDowntimeProjects(models, daysPassed, stillEligible)
		if (outcome.completed.isEmpty() && outcome.paused.isEmpty() &&
			outcome.projects == models
		) {
			return@forEach
		}
		val byId = outcome.projects.associateBy { it.id }
		for (raw in raws) {
			val next = byId[raw.id] ?: continue // unknown-kind rows keep their stored state
			raw.daysRemaining = next.daysRemaining
			raw.status = next.status.value
			raw.pauseReason = next.pauseReason
		}
		// History cap: oldest COMPLETED rows only -- in-progress and paused work is never pruned
		// (a dropped row silently cancels work a player is waiting on).
		val completedCount = raws.count { it.status == DowntimeStatus.COMPLETED.value }
		if (completedCount > DOWNTIME_HISTORY_CAP) {
			var toDrop = completedCount - DOWNTIME_HISTORY_CAP
			kingdom.downtimeProjects = raws.filter { raw ->
				if (toDrop > 0 && raw.status == DowntimeStatus.COMPLETED.value) {
					toDrop--
					false
				} else {
					true
				}
			}.toTypedArray()
		}
		actor.setKingdom(kingdom)
		// One GM-whispered offer per completion, on the tick that consumed the last day only --
		// the pure core reports completion exactly once, so a later jump cannot re-offer.
		if (outcome.completed.isNotEmpty()) {
			val gmIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
			if (gmIds.isNotEmpty()) {
				val rawByIdAfter = (kingdom.downtimeProjects ?: emptyArray()).associateBy { it.id }
				for (done in outcome.completed) {
					val raw = rawByIdAfter[done.id]
					postChatTemplate(
						templatePath = "chatmessages/downtime-complete.hbs",
						templateContext = downtimeCompleteContext(
							actorUuid = actor.uuid,
							project = done,
							targetRef = raw?.targetRef,
						),
						whisper = gmIds,
					)
				}
			}
		}
	}
}

/**
 * Evaluates calendar-dated pressure schedules over the day window just crossed
 * (`docs/plans/2026-07-09-plan-scheduled-pressure-engine.md` §4/§5, phase 2).
 *
 * The window is (fromDay, toDay] in world day numbers, so a week-long jump yields every weekly
 * firing inside it. Resolution is POLLED against stored quest/threat state rather than hooked —
 * no completion hook exists for either, and polling also catches a quest completed while the
 * module was disabled. Phase 2 posts a plain GM-whispered line per firing; the digest card and
 * its offer buttons are phase 3, so firing state (lastFiredDay, escalationCount) is stamped here
 * and nothing else changes.
 */
private suspend fun tickScheduledPressures(game: Game, worldTime: Int, daysPassed: Int) {
	val toDay = worldTime.floorDiv(DAY_SECONDS)
	val fromDay = toDay - daysPassed
	val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
	game.getKingdomActors().forEach { actor ->
		val kingdom = actor.getKingdom() ?: return@forEach
		val raws = kingdom.scheduledPressures ?: return@forEach
		if (raws.isEmpty()) return@forEach
		val rawById = raws.associateBy { it.id }
		val models = raws.mapNotNull { it.toModel() }
		if (models.isEmpty()) return@forEach

		val isResolved: (ScheduledPressure) -> Boolean = resolved@{ schedule ->
			val raw = rawById[schedule.id] ?: return@resolved false
			isPressureResolved(raw, kingdom)
		}

		val firings = dueFirings(models, fromDay, toDay, isResolved)
		if (firings.isEmpty()) return@forEach
		for (firing in firings) {
			val raw = rawById[firing.schedule.id] ?: continue
			raw.lastFiredDay = firing.day
			raw.escalationCount = firing.escalation
		}
		actor.setKingdom(kingdom)
		// One whispered digest per tick (plan SS7): rows are OFFERS -- payloads apply on the
		// card's confirm buttons, never here.
		if (gmUserIds.isNotEmpty()) {
			postChatTemplate(
				templatePath = "chatmessages/pressure-digest.hbs",
				templateContext = buildPressureDigestContext(
					firings = firings,
					rawById = rawById,
					actorUuid = actor.uuid,
					currentDay = toDay,
				),
				whisper = gmUserIds,
			)
		}
	}
}
