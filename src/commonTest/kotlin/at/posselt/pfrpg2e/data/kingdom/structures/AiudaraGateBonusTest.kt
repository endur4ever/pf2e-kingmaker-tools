package at.posselt.pfrpg2e.data.kingdom.structures

import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementLayoutType
import at.posselt.pfrpg2e.data.kingdom.settlements.SettlementType
import at.posselt.pfrpg2e.kingdom.modifiers.evaluation.SettlementData
import at.posselt.pfrpg2e.kingdom.modifiers.evaluation.evaluateSettlement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Aiudara Gate's whole mechanical payload is a +3 item bonus to Relocate Capital
 * (house-rules.md, "Aiudara Gate"). The structure JSON shipped, but nothing ever proved the bonus
 * survives settlement evaluation and reaches a check — which was this card's stated acceptance.
 *
 * These mirror data/structures/aiudara-gate.json: level 10, one lot, a single activityBonusRule of
 * +3 on relocate-capital, which Structure.kt turns into a StructureBonus with a null skill.
 *
 * See card t_2d6e89b0.
 */
class AiudaraGateBonusTest {
    private val gate = Structure(
        name = "Aiudara Gate",
        id = "aiudara-gate",
        uuid = "",
        actorUuid = "",
        level = 10,
        lots = 1,
        traits = setOf(StructureTrait.BUILDING),
        bonuses = setOf(
            StructureBonus(skill = null, activity = "relocate-capital", value = 3),
        ),
    )

    /**
     * [occupiedBlocks] drives settlement SIZE, and size caps the item bonus a settlement can grant
     * (SettlementSize.maxItemBonus: village 1, rising with size). The gate's +3 therefore only
     * lands in full in a large enough settlement — 20 blocks is a metropolis.
     */
    private fun evaluate(
        structures: List<Structure>,
        kingdomLevel: Int = 10,
        capAtKingdomLevel: Boolean = false,
        occupiedBlocks: Int = 20,
    ) = evaluateSettlement(
        data = SettlementData(
            name = "Capital",
            id = "capital",
            type = SettlementType.CAPITAL,
            waterBorders = 0,
            occupiedBlocks = occupiedBlocks,
            isSecondaryTerritory = false,
            layoutType = SettlementLayoutType.RIGID,
        ),
        structures = structures,
        allStructuresStack = false,
        allowCapitalInvestmentInCapitalWithoutBank = false,
        kingdomLevel = kingdomLevel,
        capStructureBonusAtKingdomLevel = capAtKingdomLevel,
        blocks = emptyList(),
    )

    @Test
    fun aBuiltGateGrantsPlusThreeToRelocateCapital() {
        val bonus = evaluate(listOf(gate)).bonuses.single { it.activity == "relocate-capital" }
        assertEquals(3, bonus.value)
        assertEquals("Capital", bonus.locatedIn)
        assertTrue("Aiudara Gate" in bonus.structureNames)
    }

    @Test
    fun theBonusCarriesNoSkillSoItCannotLeakOntoASkillCheck() {
        // activityBonusRules become StructureBonus(skill = null); a non-null skill here would make
        // the gate silently buff every check of that skill rather than one activity.
        assertEquals(null, evaluate(listOf(gate)).bonuses.single { it.activity == "relocate-capital" }.skill)
    }

    @Test
    fun theGateBuffsNothingElse() {
        // The gate's teleport and act-from-anywhere effects are deliberately informational text, so
        // relocate-capital must be its ONLY mechanical bonus.
        assertEquals(1, evaluate(listOf(gate)).bonuses.size)
    }

    @Test
    fun noGateMeansNoBonus() {
        assertTrue(evaluate(emptyList()).bonuses.none { it.activity == "relocate-capital" })
    }

    @Test
    fun aSmallSettlementCapsTheGateBelowItsFullBonus() {
        // Worth pinning because it is the surprise: the house rule promises +3, but PF2e caps the
        // item bonus a settlement can grant by its SIZE. A village allows only +1, so a gate built
        // in one is worth a third of its advertised value. Anyone reading the rules text and then
        // the sheet needs this to be deliberate rather than a bug.
        val village = evaluate(listOf(gate), occupiedBlocks = 1)
            .bonuses.single { it.activity == "relocate-capital" }
        assertEquals(1, village.value)
    }

    @Test
    fun aLowLevelKingdomCanHaveTheBonusCappedByItsOwnLevel() {
        // A second, independent cap — the global capStructureBonusAtKingdomLevel toggle.
        val capped = evaluate(listOf(gate), kingdomLevel = 2, capAtKingdomLevel = true)
            .bonuses.single { it.activity == "relocate-capital" }
        assertTrue(capped.value <= 3)
    }
}
