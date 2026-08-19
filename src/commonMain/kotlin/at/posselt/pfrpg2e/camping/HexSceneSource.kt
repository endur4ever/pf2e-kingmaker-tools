package at.posselt.pfrpg2e.camping

/**
 * Which scene the party's hex position must be read from.
 *
 * Camping happens at the moment a group is most likely to have left the hex map: the GM pulls up a
 * campsite battle map, an ambush scene, or an interior, and that becomes `scenes.active`. Reading
 * the party's hex position off the active scene therefore answers "which hex is the party in?" with
 * "no idea" precisely when camping asks, and every hex-derived rule silently falls back to its
 * default — auto-success never fires, per-hex encounter overrides never apply.
 *
 * The module already has the answer: Camping Settings carries a "Hexploration Scene" pointer
 * (`CampingData.worldSceneId`) naming the hex map the party travels on, and [findExistingCampsiteResult]
 * in this same package resolves campsite state from that scene by id rather than from the active one.
 * This makes hex-position lookups agree with it.
 *
 * Both inputs are nullable to keep "no such scene" distinct from "a scene that is not a hex grid":
 * a configured scene that is not hexagonal is a misconfiguration to fall through, not a reason to
 * give up on the active scene.
 *
 * @param configuredSceneIsHexagonal whether the configured hexploration scene is a hex grid; null when none is configured or it no longer exists
 * @param activeSceneIsHexagonal whether the active scene is a hex grid; null when there is no active scene
 */
enum class HexSceneSource {
    /** Read from the scene named by `CampingData.worldSceneId`. */
    CONFIGURED,

    /** No usable configured scene; the active scene is itself a hex map. */
    ACTIVE,

    /** No hex map available — the party has no derivable hex position. */
    NONE,
}

fun hexSceneSource(
    configuredSceneIsHexagonal: Boolean?,
    activeSceneIsHexagonal: Boolean?,
): HexSceneSource = when {
    configuredSceneIsHexagonal == true -> HexSceneSource.CONFIGURED
    activeSceneIsHexagonal == true -> HexSceneSource.ACTIVE
    else -> HexSceneSource.NONE
}
