package com.foundryvtt.pf2e.actions

import js.collections.JsSet

external class StatisticDifficultyClass {
    val modifiers: Array<ModifierPF2e>
    val options: JsSet<String>
    // Partial binding. `StatisticDifficultyClass` verified present in served pf2e.mjs 8.1.2 (2026-09-02).
}