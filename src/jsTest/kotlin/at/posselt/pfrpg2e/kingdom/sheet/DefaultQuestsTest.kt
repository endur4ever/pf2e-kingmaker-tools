package at.posselt.pfrpg2e.kingdom.sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultQuestsTest {
    @Test
    fun newKingdomSeedsNamingEkundayosDog() {
        val defaults = createKingdomDefaults("Test")
        val quest = (defaults.quests ?: emptyArray())
            .find { it.id == "naming-ekundayos-dog" }
        assertNotNull(quest, "default kingdom should seed the Naming Ekundayo's Dog quest")
        assertEquals("Naming Ekundayo's Dog", quest.title)
        assertEquals("Ekundayo", quest.giver)
        assertEquals("other", quest.type)
        assertEquals("active", quest.status)
        assertEquals(false, quest.hidden)
        assertEquals(10, quest.rewards.xp)
        assertEquals("+1 Influence with Ekundayo", quest.rewards.other)
        assertTrue(quest.flavorTextCompleted.isNotBlank())
        assertTrue(!quest.notes.isNullOrBlank(), "seed quest should carry GM notes")
    }

    @Test
    fun newKingdomSeedsMeetingNokNok() {
        val defaults = createKingdomDefaults("Test")
        val quest = (defaults.quests ?: emptyArray())
            .find { it.id == "meeting-nok-nok" }
        assertNotNull(quest, "default kingdom should seed the Meeting Nok-Nok quest")
        assertEquals("Meeting Nok-Nok", quest.title)
        assertEquals("Nok-Nok", quest.giver)
        assertEquals("other", quest.type)
        assertEquals("active", quest.status)
        assertEquals(false, quest.hidden)
        assertEquals("Recruit Nok-Nok, an Irongut goblin companion", quest.rewards.other)
        assertTrue(quest.flavorTextCompleted.isNotBlank())
        assertTrue(!quest.notes.isNullOrBlank(), "seed quest should carry GM notes")
    }

    @Test
    fun seedsAllExpectedQuests() {
        val expected = setOf(
            "naming-ekundayos-dog",
            "meeting-nok-nok",
            "to-ask-for-forgiveness",
            "blood-calling",
            "the-inconsequent-debates",
            "sir-frederos-challenge",
            "defeating-nargloom",
            "a-missing-brother",
            "a-ladys-desire",
            "the-omelet-king",
            "wanted-manticore",
            "the-mammoths-shame",
            "ch4-the-slain-townsfolk",
            "ch4-the-rabble-rouser",
            "ch4-desperate-spies",
            "ch4-the-cult-of-gyronna",
            "ch4-the-founding-of-tatzlford",
            "ch4-return-of-the-black-tears",
            "ch4-troll-sightings",
            "ch4-trouble-with-a-weird-gnome",
            "ch4-troll-slaying",
            "ch4-hunting-the-beast",
            "meeting-kalikke-kanerah",
            "meeting-octavia-regongar",
        )
        val ids = createKingdomDefaults("Test").quests?.map { it.id }?.toSet() ?: emptySet()
        assertEquals(expected, ids, "default kingdom should seed exactly the expected quest set")
    }

    @Test
    fun everySeedQuestIsWellFormed() {
        defaultQuests().forEach { q ->
            assertTrue(q.id.isNotBlank(), "quest id must not be blank")
            assertTrue(q.title.isNotBlank(), "quest ${q.id} must have a title")
            assertTrue(q.giver.isNotBlank(), "quest ${q.id} must have a giver")
            assertTrue(q.description.isNotBlank(), "quest ${q.id} must have a description")
            assertTrue(q.flavorTextCompleted.isNotBlank(), "quest ${q.id} must have completion flavor text")
            assertEquals("active", q.status, "seed quest ${q.id} should start active")
            assertEquals(false, q.hidden, "seed quest ${q.id} should be visible")
        }
    }

    @Test
    fun seedQuestIdsAreStableAndUnique() {
        val ids = defaultQuests().map { it.id }
        assertEquals(ids.size, ids.toSet().size, "seed quest ids must be unique")
        assertTrue(ids.none { it.startsWith("quest-") }, "seed ids must be stable, not timestamp-based")
    }
}
