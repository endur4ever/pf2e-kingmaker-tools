package at.posselt.pfrpg2e.camping

/**
 * A rumor surfaced by a RUMOR-category encounter (roadmap #11). A rumor flagged
 * as a quest hook can be converted into a [at.posselt.pfrpg2e.kingdom.CampaignQuest]
 * record; [isConverted]/[convertedQuestId] track that conversion.
 */
data class Rumor(
    val text: String,
    val isQuestHook: Boolean = false,
    val questTemplateId: String? = null,
    val questTemplateName: String? = null,
    val location: String? = null,
    val sourceRegion: String? = null,
    val isConverted: Boolean = false,
    val convertedQuestId: String? = null,
    /** Stable identity; empty only on rows written before the lifecycle feature existed. */
    val id: String = "",
    /** World day the rumor entered play. Null: adopt the first day it is seen ticking (a row
     *  written before this field existed must not be treated as infinitely old). */
    val bornDay: Int? = null,
    val state: RumorState = RumorState.FRESH,
    /** The GM's private assessment. Null = unassessed; players never see it either way. */
    val veracity: RumorVeracity? = null,
    /** Day an expiry beat was offered, so a declined beat never re-offers. */
    val beatOfferedDay: Int? = null,
    /** Machine-readable sibling of the prose [location]; enables the hex-hook conversion. */
    val sourceHexKey: String? = null,
)

enum class RumorState(val value: String) {
    FRESH("fresh"),
    STALE("stale"),
    EXPIRED("expired"),
    CONVERTED("converted"),
    PINNED("pinned");

    companion object {
        fun fromValue(value: String?): RumorState? = entries.find { it.value == value }
    }
}

enum class RumorVeracity(val value: String) {
    TRUE("true"),
    DISTORTED("distorted"),
    FALSE("false");

    companion object {
        fun fromValue(value: String?): RumorVeracity? = entries.find { it.value == value }
    }
}
