package at.posselt.pfrpg2e.kingdom

/**
 * Pure logic for anarchy activity gating — lives in [commonMain] so it is
 * unit-testable without a JS runtime.
 *
 * When a kingdom is in anarchy (unrest ≥ anarchy threshold) and the
 * `enableAnarchyActivityGating` setting is on, every activity whose id is
 * **not** in [allowedDuringAnarchy] is disabled in the UI.
 */

/** Activity ids that remain performable during anarchy. */
val allowedDuringAnarchy: Set<String> = setOf("quell-unrest")

/** Returns `true` when the kingdom is in anarchy (unrest ≥ anarchyAt). */
fun isInAnarchy(currentUnrest: Int, anarchyAt: Int): Boolean =
    currentUnrest >= anarchyAt

/**
 * Returns `true` when [activityId] is allowed during anarchy.
 *
 * The gate only activates when the kingdom is actually in anarchy **and**
 * the setting is enabled; this pure function captures the activity-level
 * check so the caller can fold in the setting + current-unrest values.
 */
fun activityAllowedDuringAnarchy(activityId: String): Boolean =
    activityId in allowedDuringAnarchy
