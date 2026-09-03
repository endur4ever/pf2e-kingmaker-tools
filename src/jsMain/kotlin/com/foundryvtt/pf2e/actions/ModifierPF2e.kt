package com.foundryvtt.pf2e.actions


/**
 * Generic superclass that bundles functionality but can not be checked at runtime
 * because the class is not exposed n the scope
 */
@JsName("game.pf2e.Modifier")
@Suppress("NAME_CONTAINS_ILLEGAL_CHARS")
external class ModifierPF2e {
    // TYPE-ONLY binding: passed in `modifiers` arrays, never constructed here. In PF2e 8.1.2 the
    // runtime class is exported as `Modifier` (`Modifier = class Modifier2` in the served bundle);
    // `ModifierPF2e` no longer exists under that name. Constructing one from Kotlin would need a
    // @JsName("Modifier") and the served constructor signature -- add both together if ever needed.
}