package com.foundryvtt.pf2e.actor

import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.abstract.DatabaseDeleteOperation
import com.foundryvtt.core.abstract.DatabaseUpdateOperation
import com.foundryvtt.core.documents.Actor
import com.foundryvtt.core.documents.collections.EmbeddedCollection
import com.foundryvtt.pf2e.item.PF2EItem
import com.foundryvtt.pf2e.system.IntValue
import com.foundryvtt.pf2e.system.MaxValue
import js.objects.unsafeJso
import kotlinx.js.JsPlainObject
import kotlin.js.Promise

@JsPlainObject
external interface PF2EArmyTraits {
    val type: String // 'skirmisher' | 'cavalry' | 'siege' | 'infantry'
    val rarity: String // 'common' | 'uncommon' | 'rare' | 'unique'
}

@JsPlainObject
external interface PF2EArmyDetails {
    var level: IntValue
    var alliance: String // party or ...
}

@JsPlainObject
external interface PF2EArmyHp : MaxValue {
    var temp: Int

    /** Rout threshold from the sheet (system schema keeps it on `attributes.hp`). */
    var routThreshold: Int
}

/**
 * Verified against the PF2e system's `ArmySystemData.defineSchema()` (v8.1.2): `hp` is the ONLY
 * child of `attributes` — army AC is the TOP-LEVEL `system.ac` schema (value + potency), and the
 * saves live under `system.saves` (maneuver/morale). Do not re-add `ac` here; the original guess
 * pointed battle stats at a nonexistent path and every custom army silently fell back to the
 * workbook level table.
 */
@JsPlainObject
external interface PF2EArmyAttributes {
    var hp: PF2EArmyHp
}

/** Top-level `system.ac`: base value plus armor potency rune. */
@JsPlainObject
external interface PF2EArmyAc {
    var value: Int
    var potency: Int
}

/** `system.saves`: maneuver (strong by default) and morale (weak by default), flat modifiers. */
@JsPlainObject
external interface PF2EArmySaves {
    var maneuver: Int
    var morale: Int
}

/** One army weapon (`system.weapons.melee`/`.ranged`): name + weapon potency rune. */
@JsPlainObject
external interface PF2EArmyWeapon {
    var name: String
    var potency: Int
}

@JsPlainObject
external interface PF2EArmyWeapons {
    var melee: PF2EArmyWeapon?
    var ranged: PF2EArmyWeapon?
}

@JsPlainObject
external interface PF2EArmyData {
    val recruitmentDC: Int
    val consumption: Int
    val scouting: Int
    val traits: PF2EArmyTraits
    val details: PF2EArmyDetails
    val ac: PF2EArmyAc
    val saves: PF2EArmySaves
    val weapons: PF2EArmyWeapons
    val attributes: PF2EArmyAttributes
    val items: EmbeddedCollection<PF2EItem>
}

// required to make instance of work, but since the classes are not registered here
// at page load, we can't use @file:JsQualifier
@JsName("CONFIG.PF2E.Actor.documentClasses.army")
@Suppress("NAME_CONTAINS_ILLEGAL_CHARS")
external class PF2EArmy : PF2EActor {
    companion object : DocumentStatic<PF2EArmy>

    override fun delete(operation: DatabaseDeleteOperation): Promise<PF2EArmy>
    override fun update(data: AnyObject, operation: DatabaseUpdateOperation): Promise<PF2EArmy?>

    val system: PF2EArmyData
}

@Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE", "UNCHECKED_CAST")
fun PF2EArmy.update(data: PF2EArmy, operation: DatabaseUpdateOperation = unsafeJso()): Promise<PF2EArmy?> =
    update(data as AnyObject, operation)