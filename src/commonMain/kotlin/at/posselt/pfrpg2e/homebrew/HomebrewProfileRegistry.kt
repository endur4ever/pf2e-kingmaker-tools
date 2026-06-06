package at.posselt.pfrpg2e.homebrew

/**
 * Registry holding all homebrew profiles and the active profile id.
 * The registry is stored in a Foundry game setting as an object.
 */
data class HomebrewProfileRegistry(
    val version: Int = 1,
    val activeProfileId: String? = null,
    val profiles: List<HomebrewRulesProfile> = emptyList(),
)
