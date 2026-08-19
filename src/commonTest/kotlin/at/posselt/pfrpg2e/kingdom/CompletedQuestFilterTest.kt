package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompletedQuestFilterTest {
    private fun quest(id: String, title: String, level: Int? = null, category: String? = null) =
        CompletedQuestEntry(id = id, title = title, level = level, category = category)

    private val quests = listOf(
        quest("1", "Slay the Dragon", level = 5, category = "combat"),
        quest("2", "Recover the Relic", level = 8, category = "exploration"),
        quest("3", "Broker Peace", level = 3, category = "diplomacy"),
        quest("4", "Dragon Cult", level = null, category = "combat"),
    )

    @Test
    fun titleSearchIsCaseInsensitiveSubstring() {
        val r = filterCompletedQuests(quests, CompletedQuestFilter(titleQuery = "dragon"))
        assertEquals(setOf("1", "4"), r.map { it.id }.toSet())
    }

    @Test
    fun levelRangeExcludesOutOfRangeAndNullLevels() {
        val r = filterCompletedQuests(quests, CompletedQuestFilter(minLevel = 4, maxLevel = 8))
        assertEquals(setOf("1", "2"), r.map { it.id }.toSet())  // level 3 out, null-level excluded
    }

    @Test
    fun categoryIsExactMatch() {
        val r = filterCompletedQuests(quests, CompletedQuestFilter(category = "combat"))
        assertEquals(setOf("1", "4"), r.map { it.id }.toSet())
    }

    @Test
    fun blankFiltersReturnEverything() {
        val r = filterCompletedQuests(quests, CompletedQuestFilter(titleQuery = "   ", category = ""))
        assertEquals(4, r.size)
    }

    @Test
    fun combinedFiltersNarrow() {
        val r = filterCompletedQuests(quests, CompletedQuestFilter(titleQuery = "dragon", category = "combat", minLevel = 4))
        assertEquals(listOf("1"), r.map { it.id })  // "Dragon Cult" has null level -> excluded by minLevel
    }

    @Test
    fun pagingCollapsesToLimitWithHiddenCount() {
        val many = (1..40).map { quest(it.toString(), "Quest $it") }
        val page = pageCompletedQuests(many, limit = 25, expanded = false)
        assertEquals(25, page.shown.size)
        assertEquals(40, page.total)
        assertEquals(15, page.hiddenCount)
    }

    @Test
    fun collapsingKeepsTheNEWESTEntriesNotTheOldest() {
        // The existing paging tests assert only SIZES, so take(limit) and takeLast(limit) are
        // indistinguishable to them -- yet that is exactly the difference between showing the most
        // recent 25 quests and showing the 25 oldest, which is the whole point of the collapse.
        // The input contract is newest-first, so the kept slice must be the FRONT of the list.
        val many = (1..40).map { quest(it.toString(), "Quest $it") }
        val page = pageCompletedQuests(many, limit = 25, expanded = false)
        assertEquals("1", page.shown.first().id, "the newest quest must survive the collapse")
        assertEquals("25", page.shown.last().id, "the 25 kept must be the newest 25, not the oldest")
    }

    @Test
    fun expandingPreservesTheOriginalOrder() {
        val many = (1..40).map { quest(it.toString(), "Quest $it") }
        val page = pageCompletedQuests(many, limit = 25, expanded = true)
        assertEquals(many.map { it.id }, page.shown.map { it.id })
    }

    @Test
    fun pagingNeverInventsOrReordersEntries() {
        // A slice must be a genuine prefix of the input -- no reordering, no duplicates.
        val many = (1..40).map { quest(it.toString(), "Quest $it") }
        val shown = pageCompletedQuests(many, limit = 10, expanded = false).shown
        assertEquals(many.take(10).map { it.id }, shown.map { it.id })
    }

    @Test
    fun pagingExpandedShowsEverything() {
        val many = (1..40).map { quest(it.toString(), "Quest $it") }
        val page = pageCompletedQuests(many, limit = 25, expanded = true)
        assertEquals(40, page.shown.size)
        assertEquals(0, page.hiddenCount)
    }

    @Test
    fun pagingUnderLimitHasNoHidden() {
        val page = pageCompletedQuests(quests, limit = 25, expanded = false)
        assertEquals(4, page.shown.size)
        assertEquals(0, page.hiddenCount)
        assertTrue(page.total == 4)
    }
}
