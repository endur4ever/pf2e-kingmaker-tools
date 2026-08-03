package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.kingdom.data.RawPartyMemberInfluence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyInfluenceContextTest {

    private fun stored(companionId: String, uuid: String, influence: Int): RawPartyMemberInfluence =
        RawPartyMemberInfluence(companionId = companionId, uuid = uuid, influence = influence)

    private fun companion(id: String, role: String = "Companion") =
        CompanionRef(companionId = id, name = id.uppercase(), img = null, roleLabel = role)

    private val twoMembers = listOf(
        PartyMemberRef("m1", "Valeros", null),
        PartyMemberRef("m2", "Kyra", null),
    )

    @Test
    fun `each companion parents one influence row per party member`() {
        val ctx = buildPartyInfluenceContext(
            companions = listOf(companion("amiri"), companion("nok", role = "NPC")),
            members = twoMembers,
            stored = arrayOf(stored("amiri", "m1", 5)),
            isGM = true,
        )
        assertEquals(2, ctx.companions.size)
        val amiri = ctx.companions[0]
        assertEquals("amiri", amiri.companionId)
        assertEquals(2, amiri.members.size)
        assertEquals(5, amiri.members[0].influence)   // stored
        assertEquals(0, amiri.members[1].influence)   // default
        assertEquals(0, ctx.companions[1].members[0].influence) // other companion unaffected
        assertEquals("NPC", ctx.companions[1].roleLabel)
        assertTrue(ctx.isGM)
    }

    @Test
    fun `influence is per companion-member pair, not shared`() {
        val ctx = buildPartyInfluenceContext(
            companions = listOf(companion("amiri"), companion("linzi")),
            members = twoMembers,
            stored = arrayOf(stored("amiri", "m1", 8), stored("linzi", "m1", 2)),
            isGM = false,
        )
        assertEquals(8, ctx.companions[0].members[0].influence)
        assertEquals(2, ctx.companions[1].members[0].influence)
        assertFalse(ctx.isGM)
    }

    @Test
    fun `influence is clamped into the bar and percent scales against the cap of 12`() {
        val ctx = buildPartyInfluenceContext(
            companions = listOf(companion("amiri")),
            members = listOf(PartyMemberRef("m1", "Valeros", null)),
            stored = arrayOf(stored("amiri", "m1", 99)),
            isGM = true,
        )
        val row = ctx.companions[0].members[0]
        assertEquals(12, row.influence)
        assertEquals(100, row.influencePercent)
        assertEquals(12, ctx.maxInfluence)
    }

    @Test
    fun `stored rows for absent companions or members are ignored`() {
        val ctx = buildPartyInfluenceContext(
            companions = listOf(companion("amiri")),
            members = listOf(PartyMemberRef("m1", "Valeros", null)),
            stored = arrayOf(
                stored("amiri", "m1", 4),
                stored("ghost", "m1", 7),   // companion gone
                stored("amiri", "mX", 9),   // member gone
            ),
            isGM = true,
        )
        assertEquals(1, ctx.companions.size)
        assertEquals(1, ctx.companions[0].members.size)
        assertEquals(4, ctx.companions[0].members[0].influence)
    }

    @Test
    fun `withInfluence upserts and clamps per pair without touching the receiver`() {
        val start = arrayOf(stored("amiri", "m1", 3))

        val raised = start.withInfluence("amiri", "m1", 20)
        assertEquals(12, raised.first { it.companionId == "amiri" && it.uuid == "m1" }.influence)
        assertEquals(1, raised.size)

        val added = start.withInfluence("amiri", "m2", -5)
        assertEquals(0, added.first { it.uuid == "m2" }.influence)
        assertEquals(2, added.size)

        // a different companion is a different pair
        val other = start.withInfluence("linzi", "m1", 6)
        assertEquals(2, other.size)
        assertEquals(3, other.first { it.companionId == "amiri" }.influence)

        // receiver untouched
        assertEquals(3, start.single().influence)
    }
}
