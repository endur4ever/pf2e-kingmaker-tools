package at.posselt.pfrpg2e.camping

import kotlinx.js.JsPlainObject

/**
 * The spawnable creature manifest for the Encounter Stager
 * (`docs/plans/2026-07-09-plan-encounter-stager.md` 2.2).
 *
 * The curator's own pipeline carries no creature reference at all -- it reads a roll table's
 * `.text` and discards the result's document ref -- so this is the missing "N of creature X"
 * model. Every field is nullable or defaulted: a manifest is action-shaped, held in memory for a
 * preview and only persisted on a pending-encounter hex.
 */
@JsPlainObject
external interface RawEncounterCreature {
    /** Full document UUID: a world Actor or Compendium.<pack>.Actor.<id>. */
    var uuid: String
    /** Tokens to spawn; below 1 contributes nothing rather than subtracting. */
    var count: Int
    /** null | "elite" | "weak" -- RECORDED in v1, applied in v2 (plan section 7). */
    var adjustment: String?
    /** Cached label so the dialog can list a compendium creature without resolving it. */
    var displayName: String?
}

@JsPlainObject
external interface RawEncounterManifest {
    var creatures: Array<RawEncounterCreature>?
    /** Overrides the resolver's distance when the GM wants a different opening range. */
    var startDistanceFt: Int?
    var xpBudgetNote: String?
}

/** Never-null creature view; a manifest with no array behaves as an empty one. */
fun RawEncounterManifest?.creatureList(): List<RawEncounterCreature> =
    this?.creatures?.toList() ?: emptyList()

/** The pure mirrors the commonMain math works on. */
fun RawEncounterManifest?.toStageCreatures(): List<StageCreature> =
    creatureList().map { StageCreature(uuid = it.uuid, count = it.count, adjustment = it.adjustment) }

/** Total tokens this manifest spawns; 0 disables the Stage button. */
fun RawEncounterManifest?.spawnCount(): Int = manifestSpawnCount(toStageCreatures())
