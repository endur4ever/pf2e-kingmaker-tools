package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.RivalGrowthProfile
import kotlinx.js.JsPlainObject

/**
 * Chapter growth presets, one JSON file per profile in `data/rival-growth-profiles`
 * (`docs/plans/2026-07-09-plan-rival-realms.md` SS3.4).
 *
 * A DIRECTORY rather than a single file because the build's combineJsonFiles walks the
 * subdirectories of `data` and emits one bundle per subdirectory; a top-level file is skipped
 * outright and the @JsModule below would not resolve. Adding the directory needed no
 * build.gradle.kts change -- verified by building the bundle.
 */
@JsPlainObject
external interface RawRivalGrowthProfile {
    var id: String
    var sizePerTurn: Double
    var famePerTurn: Double
    var armyPerTurn: Double
}

@JsModule("./rival-growth-profiles.json")
private external val rivalGrowthProfiles: Array<RawRivalGrowthProfile>

/**
 * Presets by id. Built once per call from the bundle -- the array is a handful of rows, and a
 * cached map would have to be invalidated on nothing, since the bundle is compile-time constant.
 */
fun rivalGrowthProfilesById(): Map<String, RivalGrowthProfile> =
    rivalGrowthProfiles.associate { raw ->
        raw.id to RivalGrowthProfile(
            sizePerTurn = raw.sizePerTurn,
            famePerTurn = raw.famePerTurn,
            armyPerTurn = raw.armyPerTurn,
        )
    }
