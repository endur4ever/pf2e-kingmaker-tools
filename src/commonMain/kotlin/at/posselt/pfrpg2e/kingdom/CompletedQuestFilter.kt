package at.posselt.pfrpg2e.kingdom

/** Default number of completed quests shown before the "show all (N)" expander. */
const val COMPLETED_QUESTS_DEFAULT_LIMIT = 25

/** The minimal view of a completed/failed quest the filter + pager need (mapped from RawQuest). */
data class CompletedQuestEntry(
    val id: String,
    val title: String,
    val level: Int?,
    val category: String?,
)

/** The shared filter-bar state applied to the completed-quests section. */
data class CompletedQuestFilter(
    val titleQuery: String? = null,
    val minLevel: Int? = null,
    val maxLevel: Int? = null,
    val category: String? = null,
)

/**
 * Filter completed quests by the (shared) filter bar: case-insensitive title substring, an inclusive
 * level range (a level bound excludes quests with no level), and exact category. A blank/null field
 * is not applied. Pure — the section context maps RawQuest -> [CompletedQuestEntry] and calls this.
 */
fun filterCompletedQuests(
    quests: List<CompletedQuestEntry>,
    filter: CompletedQuestFilter,
): List<CompletedQuestEntry> {
    val query = filter.titleQuery?.trim()?.takeIf { it.isNotEmpty() }?.lowercase()
    val category = filter.category?.takeIf { it.isNotBlank() }
    return quests.filter { quest ->
        (query == null || quest.title.lowercase().contains(query)) &&
            (filter.minLevel == null || (quest.level != null && quest.level >= filter.minLevel)) &&
            (filter.maxLevel == null || (quest.level != null && quest.level <= filter.maxLevel)) &&
            (category == null || quest.category == category)
    }
}

/** A page of completed quests: what to show, the total, and how many are collapsed behind the expander. */
data class CompletedQuestPage(
    val shown: List<CompletedQuestEntry>,
    val total: Int,
    val hiddenCount: Int,
)

/**
 * Collapse a filtered (newest-first) completed-quest list to [limit] entries unless [expanded]. No
 * data is dropped — completed quests remain persisted campaign history; this is display-only slicing.
 */
fun pageCompletedQuests(
    filtered: List<CompletedQuestEntry>,
    limit: Int = COMPLETED_QUESTS_DEFAULT_LIMIT,
    expanded: Boolean = false,
): CompletedQuestPage {
    val total = filtered.size
    val shown = if (expanded || total <= limit) filtered else filtered.take(limit)
    return CompletedQuestPage(shown = shown, total = total, hiddenCount = (total - shown.size).coerceAtLeast(0))
}
