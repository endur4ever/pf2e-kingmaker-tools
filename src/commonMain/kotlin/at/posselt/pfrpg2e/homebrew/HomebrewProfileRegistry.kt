package at.posselt.pfrpg2e.homebrew

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Registry holding all homebrew profiles and the active profile id.
 * The registry is stored in a Foundry game setting as a JSON string.
 */
@Serializable
data class HomebrewProfileRegistry(
    val version: Int = 1,
    val activeProfileId: String? = null,
    val profiles: List<HomebrewRulesProfile> = emptyList(),
) {
    fun toJson(): String = codec.encodeToString(serializer(), this)

    companion object {
        private val codec = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(text: String): HomebrewProfileRegistry? =
            runCatching { codec.decodeFromString(serializer(), text) }.getOrNull()
    }
}
