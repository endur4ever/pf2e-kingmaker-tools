package at.posselt.pfrpg2e.homebrew

/**
 * Data class representing a homebrew rules profile.
 * Contains metadata and the actual rules overrides.
 */
data class HomebrewRulesProfile(
    val id: String,
    val name: String,
    val version: Int = 1,
    val isActive: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val description: String? = null,
    val rules: HomebrewRules,
)
