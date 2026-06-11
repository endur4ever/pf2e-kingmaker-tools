package at.posselt.pfrpg2e.kingdom

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionPrepNarrativeGeneratorTest {

    private fun entry(
        id: String,
        name: String,
        detail: String = "",
        turnsRemaining: Int? = null,
    ) = SessionPrepEntry(
        id = id,
        name = name,
        detail = detail,
        turnsRemaining = turnsRemaining,
    )

    private fun view(
        openQuests: List<SessionPrepEntry> = emptyList(),
        activeClocks: List<SessionPrepEntry> = emptyList(),
        unresolvedEvents: List<SessionPrepEntry> = emptyList(),
        hexHooks: List<SessionPrepEntry> = emptyList(),
        companionMoments: List<SessionPrepEntry> = emptyList(),
        isGM: Boolean = true,
    ) = SessionPrepView(
        openQuests = openQuests,
        activeClocks = activeClocks,
        unresolvedEvents = unresolvedEvents,
        hexHooks = hexHooks,
        companionMoments = companionMoments,
        isGM = isGM,
    )

    @Test
    fun emptyViewReturnsEmptyString() {
        val result = SessionPrepNarrativeGenerator.generate(view())
        assertEquals("", result)
    }

    @Test
    fun emptyViewPlainTextReturnsEmptyString() {
        val result = SessionPrepNarrativeGenerator.generatePlainText(view())
        assertEquals("", result)
    }

    @Test
    fun openQuestsOnlyGeneratesQuestSection() {
        val v = view(
            openQuests = listOf(entry("q1", "Recover the Crown", detail = "Oleg")),
        )
        val html = SessionPrepNarrativeGenerator.generate(v)
        assertTrue(html.contains("<h2>Open Quests</h2>"))
        assertTrue(html.contains("Recover the Crown"))
        assertTrue(html.contains("The kingdom is currently dealing with the following open quests"))
        assertFalse(html.contains("Active Campaign Clocks"))
        assertFalse(html.contains("Unresolved Kingdom Events"))
        assertFalse(html.contains("Hex Content Hooks"))
        assertFalse(html.contains("Companion Moments"))
    }

    @Test
    fun gmSeesAllSections() {
        val v = view(
            openQuests = listOf(entry("q1", "Quest A")),
            activeClocks = listOf(entry("c1", "Rising Tension", turnsRemaining = 3)),
            unresolvedEvents = listOf(entry("e1", "Bandit Raid")),
            hexHooks = listOf(entry("h1", "Old Ruins", detail = "3,4")),
            companionMoments = listOf(entry("m1", "Amiri's Quest", detail = "Amiri", turnsRemaining = 2)),
        )
        val html = SessionPrepNarrativeGenerator.generate(v)
        assertTrue(html.contains("<h2>Open Quests</h2>"))
        assertTrue(html.contains("<h2>Active Campaign Clocks</h2>"))
        assertTrue(html.contains("<h2>Unresolved Kingdom Events</h2>"))
        assertTrue(html.contains("<h2>Hex Content Hooks</h2>"))
        assertTrue(html.contains("<h2>Companion Moments</h2>"))
        assertTrue(html.contains("3 turns remaining"))
        assertTrue(html.contains("3,4"))
        assertTrue(html.contains("[Amiri]"))
    }

    @Test
    fun playerDoesNotSeeGmOnlySections() {
        val v = view(
            openQuests = listOf(entry("q1", "Quest A")),
            activeClocks = listOf(entry("c1", "Clock A", turnsRemaining = 1)),
            unresolvedEvents = listOf(entry("e1", "Event A")),
            hexHooks = listOf(entry("h1", "Hex A")),
            companionMoments = listOf(entry("m1", "Companion A")),
            isGM = false,
        )
        val html = SessionPrepNarrativeGenerator.generate(v)
        assertTrue(html.contains("<h2>Open Quests</h2>"))
        assertFalse(html.contains("Active Campaign Clocks"))
        assertFalse(html.contains("Unresolved Kingdom Events"))
        assertTrue(html.contains("<h2>Hex Content Hooks</h2>"))
        assertTrue(html.contains("<h2>Companion Moments</h2>"))
    }

    @Test
    fun multipleQuestsAreJoined() {
        val v = view(
            openQuests = listOf(
                entry("q1", "Quest A"),
                entry("q2", "Quest B"),
                entry("q3", "Quest C"),
            ),
        )
        val html = SessionPrepNarrativeGenerator.generate(v)
        assertTrue(html.contains("Quest A, Quest B, Quest C"))
    }

    @Test
    fun clockWithoutTurnsRemainingOmitsTurnsText() {
        val v = view(
            activeClocks = listOf(entry("c1", "Slow Burn")),
        )
        val html = SessionPrepNarrativeGenerator.generate(v)
        assertTrue(html.contains("Slow Burn"))
        assertFalse(html.contains("turns remaining"))
    }

    @Test
    fun plainTextOutputContainsAllSections() {
        val v = view(
            openQuests = listOf(entry("q1", "Quest A")),
            activeClocks = listOf(entry("c1", "Clock A", turnsRemaining = 2)),
            unresolvedEvents = listOf(entry("e1", "Event A")),
            hexHooks = listOf(entry("h1", "Hex A", detail = "5,6")),
            companionMoments = listOf(entry("m1", "Companion A", detail = "Amiri", turnsRemaining = 1)),
        )
        val text = SessionPrepNarrativeGenerator.generatePlainText(v)
        assertTrue(text.contains("**Session Prep Recap**"))
        assertTrue(text.contains("**Open Quests**"))
        assertTrue(text.contains("- Quest A"))
        assertTrue(text.contains("**Active Campaign Clocks**"))
        assertTrue(text.contains("- Clock A (2 turns remaining)"))
        assertTrue(text.contains("**Unresolved Kingdom Events**"))
        assertTrue(text.contains("- Event A"))
        assertTrue(text.contains("**Hex Content Hooks**"))
        assertTrue(text.contains("- Hex A at 5,6"))
        assertTrue(text.contains("**Companion Moments**"))
        assertTrue(text.contains("- Companion A (1 turns remaining) [Amiri]"))
    }

    @Test
    fun htmlContainsSessionPrepRecapHeader() {
        val v = view(openQuests = listOf(entry("q1", "Quest A")))
        val html = SessionPrepNarrativeGenerator.generate(v)
        assertTrue(html.contains("Session Prep Recap"))
    }

    @Test
    fun hexHookWithoutDetailOmitsLocation() {
        val v = view(hexHooks = listOf(entry("h1", "Mysterious Cave")))
        val html = SessionPrepNarrativeGenerator.generate(v)
        assertTrue(html.contains("Mysterious Cave"))
        assertFalse(html.contains("at "))
    }

    @Test
    fun companionMomentWithoutDetailOmitsBrackets() {
        val v = view(companionMoments = listOf(entry("m1", "Personal Moment")))
        val html = SessionPrepNarrativeGenerator.generate(v)
        assertTrue(html.contains("Personal Moment"))
        assertFalse(html.contains("["))
    }
}
