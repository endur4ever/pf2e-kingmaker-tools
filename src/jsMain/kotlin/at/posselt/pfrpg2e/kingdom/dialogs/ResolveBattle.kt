package at.posselt.pfrpg2e.kingdom.dialogs

import at.posselt.pfrpg2e.app.FormApp
import at.posselt.pfrpg2e.app.HandlebarsRenderContext
import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import at.posselt.pfrpg2e.data.armies.ArmyCondition
import at.posselt.pfrpg2e.data.armies.BattleAction
import at.posselt.pfrpg2e.data.armies.BattleStatus
import at.posselt.pfrpg2e.data.armies.tickRound
import at.posselt.pfrpg2e.data.armies.recoverConditions
import at.posselt.pfrpg2e.kingdom.determineBattleStatus
import js.objects.recordOf
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.kingdom.awardVictoryXp
import at.posselt.pfrpg2e.kingdom.data.RawArmyBattle
import at.posselt.pfrpg2e.kingdom.data.RawBattleArmy
import at.posselt.pfrpg2e.kingdom.toBattleState
import at.posselt.pfrpg2e.kingdom.updateRawBattle
import at.posselt.pfrpg2e.utils.buildPromise
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.roll
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DataModel
import com.foundryvtt.core.abstract.DocumentConstructionContext
import com.foundryvtt.core.applications.api.HandlebarsRenderOptions
import com.foundryvtt.core.data.dsl.buildSchema
import js.core.Void
import kotlinx.coroutines.await
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.get
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.js.Promise

@JsPlainObject
external interface ResolveBattleFormData {
    var selectedAttackerIndex: Int
    var selectedDefenderIndex: Int
}

@JsExport
class ResolveBattleDataModel(
    value: AnyObject,
    options: DocumentConstructionContext?,
) : DataModel(value, options) {
    companion object {
        @JsStatic
        fun defineSchema() = buildSchema {
            int("selectedAttackerIndex")
            int("selectedDefenderIndex")
        }
    }
}

@JsPlainObject
external interface BattleArmyDisplay {
    var index: Int
    var name: String
    var level: Int
    var currentHp: Int
    var maxHp: Int
    var conditions: String
    var isDestroyed: Boolean
    var isRouted: Boolean
    /** Non-blank when the army fights with a bonus, e.g. "+1 AC (Garrison)". */
    var defenseNote: String
}

@JsPlainObject
external interface ResolveBattleContext : ValidatedHandlebarsContext {
    var battleName: String
    var round: Int
    var attackers: Array<BattleArmyDisplay>
    var defenders: Array<BattleArmyDisplay>
    var selectedAttackerIndex: Int
    var selectedDefenderIndex: Int
    var log: Array<String>
    var battleResult: String?
}

/**
 * GM-facing dialog that drives a [RawArmyBattle] round by round (roadmap #12,
 * phase 4). Each "Resolve Round" click rolls a d20 strike for the selected
 * attacker and a counter-strike for the selected defender, feeds the results
 * into the deterministic engine ([tickRound]), persists the updated battle via
 * [onSave], and posts a chat card with the round's log.
 *
 * Engine indices are global across both sides: attackers occupy
 * `0 until attackers.size`, defenders the rest (see ArmyBattleView.kt).
 */
class ResolveBattle(
    battle: RawArmyBattle,
    private val onSave: (RawArmyBattle) -> Unit,
) : FormApp<ResolveBattleContext, ResolveBattleFormData>(
    title = t("warBattle.resolveBattle"),
    template = "applications/kingdom/dialogs/resolve-battle.hbs",
    debug = false,
    dataModel = ResolveBattleDataModel::class.js,
    width = 600,
    id = "kmResolveBattle",
) {
    private var currentBattle = battle
    private var selectedAttackerIndex = firstAliveIndex(battle.attackers, offset = 0)
    private var selectedDefenderIndex = firstAliveIndex(battle.defenders, offset = battle.attackers.size)

    override fun _preparePartContext(
        partId: String,
        context: HandlebarsRenderContext,
        options: HandlebarsRenderOptions,
    ): Promise<ResolveBattleContext> = buildPromise {
        val parent = super._preparePartContext(partId, context, options).await()
        val attackerCount = currentBattle.attackers.size
        ResolveBattleContext(
            partId = parent.partId,
            isFormValid = isFormValid,
            battleName = currentBattle.name,
            round = currentBattle.round,
            attackers = currentBattle.attackers
                .mapIndexed { index, army -> army.toDisplay(index) }
                .toTypedArray(),
            defenders = currentBattle.defenders
                .mapIndexed { index, army -> army.toDisplay(attackerCount + index) }
                .toTypedArray(),
            selectedAttackerIndex = selectedAttackerIndex,
            selectedDefenderIndex = selectedDefenderIndex,
            log = currentBattle.log,
            battleResult = battleResultLabel(),
        )
    }

    override fun onParsedSubmit(value: ResolveBattleFormData): Promise<Void> = buildPromise {
        selectedAttackerIndex = value.selectedAttackerIndex
        selectedDefenderIndex = value.selectedDefenderIndex
        undefined
    }

    override fun _onClickAction(event: PointerEvent, target: HTMLElement) {
        when (target.dataset["action"]) {
            "resolve-round" -> buildPromise { resolveRound() }
        }
    }

    private suspend fun resolveRound() {
        if (currentBattle.status != BattleStatus.ACTIVE.value) return
        val state = toBattleState(currentBattle)
        val attacker = state.armies.getOrNull(selectedAttackerIndex)
        val defender = state.armies.getOrNull(selectedDefenderIndex)
        if (attacker == null || defender == null ||
            ArmyCondition.DESTROYED in attacker.conditions ||
            ArmyCondition.ROUTED in attacker.conditions ||
            ArmyCondition.DESTROYED in defender.conditions ||
            ArmyCondition.ROUTED in defender.conditions
        ) {
            return
        }

        val attackerRoll = roll("1d20", toChat = false)
        val defenderRoll = roll("1d20", toChat = false)
        val attackerMoraleRoll = roll("1d20", toChat = false)
        val defenderMoraleRoll = roll("1d20", toChat = false)

        val newState = tickRound(
            state,
            listOf(
                BattleAction(
                    actorIndex = selectedAttackerIndex,
                    targetIndex = selectedDefenderIndex,
                    roll = attackerRoll,
                    moraleRoll = attackerMoraleRoll
                ),
                BattleAction(
                    actorIndex = selectedDefenderIndex,
                    targetIndex = selectedAttackerIndex,
                    roll = defenderRoll,
                    moraleRoll = defenderMoraleRoll
                ),
            ),
        )

        val attackerCount = currentBattle.attackers.size
        val nextStatus = determineBattleStatus(newState, attackerCount)
        val finalState = if (nextStatus != BattleStatus.ACTIVE) {
            val newLogs = newState.log.toMutableList()
            val recoveredArmies = newState.armies.map { army ->
                val miredRoll = if (ArmyCondition.MIRED in army.conditions) roll("1d20", toChat = false) else 10
                val pinnedRoll = if (ArmyCondition.PINNED in army.conditions) roll("1d20", toChat = false) else 10
                val recovered = recoverConditions(army, miredRoll, pinnedRoll)
                
                if (ArmyCondition.WEARY in army.conditions) {
                    newLogs.add(t("warBattle.recovers", recordOf("name" to army.name, "condition" to t("kingdom.warfare.weary"))))
                }
                if (ArmyCondition.ROUTED in army.conditions) {
                    newLogs.add(t("warBattle.recovers", recordOf("name" to army.name, "condition" to t("kingdom.warfare.routed"))))
                }
                if (ArmyCondition.MIRED in army.conditions) {
                    if (miredRoll >= 10) {
                        newLogs.add(t("warBattle.recoversMiredSuccess", recordOf("name" to army.name, "roll" to miredRoll.toString())))
                    } else {
                        newLogs.add(t("warBattle.recoversMiredFail", recordOf("name" to army.name, "roll" to miredRoll.toString())))
                    }
                }
                if (ArmyCondition.PINNED in army.conditions) {
                    if (pinnedRoll >= 10) {
                        newLogs.add(t("warBattle.recoversPinnedSuccess", recordOf("name" to army.name, "roll" to pinnedRoll.toString())))
                    } else {
                        newLogs.add(t("warBattle.recoversPinnedFail", recordOf("name" to army.name, "roll" to pinnedRoll.toString())))
                    }
                }
                recovered
            }
            newState.copy(armies = recoveredArmies, log = newLogs)
        } else {
            newState
        }

        var updated = updateRawBattle(currentBattle, finalState, statusOverride = nextStatus)
        var roundLog = finalState.log.drop(state.log.size).map { localizeBattleLogEntry(it) }
        if (updated.status == BattleStatus.VICTORY.value) {
            val rewarded = awardVictoryXp(updated.attackers, updated.defenders)
            updated.attackers.zip(rewarded).forEach { (before, after) ->
                if (after.xp > before.xp) {
                    roundLog = roundLog + "${after.name} gains ${after.xp - before.xp} XP."
                }
            }
            updated = RawArmyBattle.copy(updated, attackers = rewarded)
        }
        currentBattle = updated

        // Keep selections on living armies for the next round
        if (ArmyCondition.DESTROYED.value in (updated.attackers.getOrNull(selectedAttackerIndex)?.conditions ?: emptyArray()) ||
            ArmyCondition.ROUTED.value in (updated.attackers.getOrNull(selectedAttackerIndex)?.conditions ?: emptyArray())
        ) {
            selectedAttackerIndex = firstAliveIndex(updated.attackers, offset = 0)
        }
        val defenderLocal = selectedDefenderIndex - updated.attackers.size
        if (ArmyCondition.DESTROYED.value in (updated.defenders.getOrNull(defenderLocal)?.conditions ?: emptyArray()) ||
            ArmyCondition.ROUTED.value in (updated.defenders.getOrNull(defenderLocal)?.conditions ?: emptyArray())
        ) {
            selectedDefenderIndex = firstAliveIndex(updated.defenders, offset = updated.attackers.size)
        }

        postChatTemplate(
            "chatmessages/army-battle.hbs",
            ChatBattleContext(
                battleName = updated.name,
                round = updated.round,
                log = roundLog.toTypedArray(),
                resultLabel = battleResultLabel(),
            ),
            rollMode = RollMode.PUBLICROLL,
        )
        onSave(updated)
        render()
    }

    private fun battleResultLabel(): String? = when (currentBattle.status) {
        BattleStatus.VICTORY.value -> t("warBattle.victory")
        BattleStatus.DEFEAT.value -> t("warBattle.defeat")
        else -> null
    }
}

/**
 * Localizes structured log entries from the battle engine.
 * Engine log prefixes: MORALE_PASS, MORALE_FAIL, COND_WEARY, COND_PINNED, COND_MIRED
 */
private fun localizeBattleLogEntry(entry: String): String {
    return if (entry.startsWith("MORALE_PASS:")) {
        val parts = entry.split(":")
        if (parts.size >= 6) {
            val name = parts[1]
            val roll = parts[2].toIntOrNull() ?: 0
            val bonus = parts[3].toIntOrNull() ?: 0
            val total = parts[4].toIntOrNull() ?: 0
            val dc = parts[5].toIntOrNull() ?: 10
            t("warBattle.moraleCheckPass", recordOf(
                "name" to name,
                "roll" to roll.toString(),
                "bonus" to bonus.toString(),
                "total" to total.toString(),
                "dc" to dc.toString(),
            ))
        } else {
            entry
        }
    } else if (entry.startsWith("MORALE_FAIL:")) {
        val parts = entry.split(":")
        if (parts.size >= 6) {
            val name = parts[1]
            val roll = parts[2].toIntOrNull() ?: 0
            val bonus = parts[3].toIntOrNull() ?: 0
            val total = parts[4].toIntOrNull() ?: 0
            val dc = parts[5].toIntOrNull() ?: 10
            t("warBattle.moraleCheckFail", recordOf(
                "name" to name,
                "roll" to roll.toString(),
                "bonus" to bonus.toString(),
                "total" to total.toString(),
                "dc" to dc.toString(),
            ))
        } else {
            entry
        }
    } else if (entry.startsWith("COND_WEARY:")) {
        val name = entry.substringAfter(":")
        t("warBattle.conditionWearyPenalty", recordOf("name" to name))
    } else if (entry.startsWith("COND_PINNED:")) {
        val name = entry.substringAfter(":")
        t("warBattle.conditionPinnedNoStrike", recordOf("name" to name))
    } else if (entry.startsWith("COND_MIRED:")) {
        // MIRED prevents advance, and the current round flow has no advance action, so this
        // line is informational only — but it still reaches the player's battle log and must
        // be localized like its WEARY and PINNED siblings.
        val name = entry.substringAfter(":")
        t("warBattle.conditionMiredNoAdvance", recordOf("name" to name))
    } else {
        entry
    }
}

@JsPlainObject
external interface ChatBattleContext {
    var battleName: String
    var round: Int
    var log: Array<String>
    var resultLabel: String?
}

private fun firstAliveIndex(armies: Array<RawBattleArmy>, offset: Int): Int {
    val local = armies.indexOfFirst {
        ArmyCondition.DESTROYED.value !in it.conditions &&
        ArmyCondition.ROUTED.value !in it.conditions
    }
    return offset + local.coerceAtLeast(0)
}

private fun RawBattleArmy.toDisplay(index: Int): BattleArmyDisplay = BattleArmyDisplay(
    index = index,
    name = name,
    level = level,
    currentHp = currentHp,
    maxHp = maxHp,
    conditions = conditions.map { t("kingdom.warfare.$it") }.joinToString(", "),
    defenseNote = (defenseBonus ?: 0).takeIf { it != 0 }
        ?.let { t("warBattle.garrisonDefenseNote", recordOf("bonus" to it.toString())) }
        ?: "",
    isDestroyed = ArmyCondition.DESTROYED.value in conditions,
    isRouted = ArmyCondition.ROUTED.value in conditions,
)
