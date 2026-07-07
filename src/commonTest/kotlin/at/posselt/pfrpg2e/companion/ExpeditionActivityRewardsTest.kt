package at.posselt.pfrpg2e.companion

import at.posselt.pfrpg2e.data.actor.Skill
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpeditionActivityRewardsTest {

    private val crit = DegreeOfSuccess.CRITICAL_SUCCESS
    private val succ = DegreeOfSuccess.SUCCESS
    private val fail = DegreeOfSuccess.FAILURE
    private val critFail = DegreeOfSuccess.CRITICAL_FAILURE

    // ── skill mapping ───────────────────────────────────────────────────────

    @Test
    fun `activity skills map recognized names in order and drop non-skills`() {
        // diplomacy catalog = [diplomacy, society]; both real skills, order preserved.
        assertEquals(listOf(Skill.DIPLOMACY, Skill.SOCIETY), expeditionActivitySkills(listOf("diplomacy", "society")))
        // hunt = [survival, hunting] — "hunting" is not a Skill and is dropped.
        assertEquals(listOf(Skill.SURVIVAL), expeditionActivitySkills(listOf("survival", "hunting")))
        // treasure-hunt = [perception, thievery] — "perception" is not in the Skill enum.
        assertEquals(listOf(Skill.THIEVERY), expeditionActivitySkills(listOf("perception", "thievery")))
        // personal-quest = [any] — nothing maps.
        assertTrue(expeditionActivitySkills(listOf("any")).isEmpty())
    }

    @Test
    fun `activity skill mapping is case-insensitive`() {
        assertEquals(listOf(Skill.CRAFTING), expeditionActivitySkills(listOf("Crafting")))
        assertEquals(listOf(Skill.ATHLETICS), expeditionActivitySkills(listOf("ATHLETICS")))
    }

    @Test
    fun `tier unit is 0 routine 1 standard 2 perilous`() {
        assertEquals(0, expeditionTierUnit("routine"))
        assertEquals(1, expeditionTierUnit("standard"))
        assertEquals(2, expeditionTierUnit("perilous"))
        assertEquals(0, expeditionTierUnit("nonsense"))
    }

    // ── hunt / craft commodities ────────────────────────────────────────────

    @Test
    fun `hunt grants food on success scaled by tier and nothing on routine or failure`() {
        assertEquals(mapOf("food" to 1), expeditionActivityReward("hunt", "standard", succ).commodities)
        assertEquals(mapOf("food" to 2), expeditionActivityReward("hunt", "perilous", crit).commodities)
        assertTrue(expeditionActivityReward("hunt", "routine", succ).commodities.isEmpty()) // unit 0
        assertTrue(expeditionActivityReward("hunt", "standard", fail).commodities.isEmpty())
    }

    @Test
    fun `craft grants lumber and ore on success`() {
        assertEquals(mapOf("lumber" to 1, "ore" to 1), expeditionActivityReward("craft", "standard", succ).commodities)
        assertEquals(mapOf("lumber" to 2, "ore" to 2), expeditionActivityReward("craft", "perilous", succ).commodities)
        assertTrue(expeditionActivityReward("craft", "standard", critFail).commodities.isEmpty())
    }

    // ── treasure-hunt loot swing ────────────────────────────────────────────

    @Test
    fun `treasure hunt gives bonus RP on crit and softens a plain failure to minor`() {
        assertEquals(1, expeditionActivityReward("treasure-hunt", "standard", crit).bonusLootRp)
        assertEquals(0, expeditionActivityReward("treasure-hunt", "standard", succ).bonusLootRp)
        assertEquals("minor", expeditionActivityReward("treasure-hunt", "standard", fail).lootTierOverride)
        assertNull(expeditionActivityReward("treasure-hunt", "standard", succ).lootTierOverride)
        // A crit failure is NOT softened.
        assertNull(expeditionActivityReward("treasure-hunt", "standard", critFail).lootTierOverride)
    }

    // ── train xp multiplier ─────────────────────────────────────────────────

    @Test
    fun `train multiplies xp on success by tier`() {
        assertEquals(1.1, expeditionActivityReward("train", "routine", succ).xpMultiplier)
        assertEquals(1.25, expeditionActivityReward("train", "standard", succ).xpMultiplier)
        assertEquals(1.5, expeditionActivityReward("train", "perilous", crit).xpMultiplier)
        assertEquals(1.0, expeditionActivityReward("train", "standard", fail).xpMultiplier) // no bonus on failure
    }

    // ── rest heal ───────────────────────────────────────────────────────────

    @Test
    fun `rest reduces injury days on success by tier`() {
        assertEquals(1, expeditionActivityReward("rest", "standard", succ).injuryDaysReduction)
        assertEquals(2, expeditionActivityReward("rest", "perilous", succ).injuryDaysReduction)
        assertEquals(0, expeditionActivityReward("rest", "routine", succ).injuryDaysReduction) // unit 0
        assertEquals(0, expeditionActivityReward("rest", "standard", fail).injuryDaysReduction)
    }

    // ── scout intel ─────────────────────────────────────────────────────────

    @Test
    fun `scout yields a tier-specific intel note on success only`() {
        assertEquals("kingdom.expeditions.intel.routine", expeditionActivityReward("scout", "routine", succ).intelNoteKey)
        assertEquals("kingdom.expeditions.intel.standard", expeditionActivityReward("scout", "standard", succ).intelNoteKey)
        assertEquals("kingdom.expeditions.intel.perilous", expeditionActivityReward("scout", "perilous", crit).intelNoteKey)
        assertNull(expeditionActivityReward("scout", "standard", fail).intelNoteKey)
    }

    @Test
    fun `unknown or plain activity yields the neutral default`() {
        val r = expeditionActivityReward("diplomacy", "standard", crit)
        assertTrue(r.commodities.isEmpty())
        assertEquals(1.0, r.xpMultiplier)
        assertEquals(0, r.injuryDaysReduction)
        assertNull(r.intelNoteKey)
        assertNull(r.lootTierOverride)
        assertEquals(0, r.bonusLootRp)
    }
}
