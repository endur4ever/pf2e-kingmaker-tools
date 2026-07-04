package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.app.ValidatedHandlebarsContext
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface ChecklistItemContext {
    val id: String
    val label: String
    val description: String
    val checked: Boolean
    val highlight: Boolean  // true when item needs attention (e.g., unrest > 0)
    val disabled: Boolean?
    val isAttention: Boolean?
    val count: Int?
    val icon: String?
}

@JsPlainObject
external interface KingdomStateContext {
    val rpNow: Int
    val rpNext: Int
    val foodNow: Int
    val foodCap: Int
    val lumberNow: Int
    val lumberCap: Int
    val luxuriesNow: Int
    val luxuriesCap: Int
    val oreNow: Int
    val oreCap: Int
    val stoneNow: Int
    val stoneCap: Int
    val consumption: Int
    val unrest: Int
    val ruin: Array<RuinContext>
    val activeModifiers: Int
}

@JsPlainObject
external interface ActivityCapContext {
    val phase: String
    val phaseLabel: String
    val current: Int
    val maximum: Int
    val isOverCap: Boolean
}

@JsPlainObject
external interface TickChangeContext {
    val category: String
    val field: String
    val displayText: String
}

@Suppress("unused")
@JsPlainObject
external interface TurnWizardContext : ValidatedHandlebarsContext {
    val kingdomName: String
    val checklist: Array<ChecklistItemContext>
    val kingdomState: KingdomStateContext
    val activityCaps: Array<ActivityCapContext>
    val previewChanges: Array<TickChangeContext>
    val showPreview: Boolean
    val canCommit: Boolean
}
