package at.posselt.pfrpg2e.data.kingdom.settlements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Workbook parity test: verifies that key expected labels from
 * Settlements!A46:A145 are present in settlementDetailsMatrixRows (M9).
 */
class SettlementDetailsMatrixWorkbookParityTest {

    // Representative labels that MUST appear in the matrix rows.
    // These are sampled from across the workbook range to verify parity.
    private val expectedLabels = setOf(
        // Headers
        "Agriculture",
        "Arts",
        "Boating",
        "Defense",
        "Engineering",
        "Exploration",
        "Folklore",
        "Industry",
        "Intrigue",
        "Magic",
        "Politics",
        "Scholarship",
        "Statecraft",
        "Trade",
        "Warfare",
        "Wilderness",
        "Any",
        "General",
        "Army",
        // Specific skill rows
        "Establish Farmland",
        "Harvest Crops",
        "Craft Luxuries",
        "Create a Masterpiece",
        "Rest and Relax (Arts)",
        "Go Fishing",
        "Rest and Relax (Boating)",
        "Fortify Hex",
        "Provide Care",
        "Build Roads",
        "Demolish",
        "Establish Work Site",
        "Establish Work Site (Lumber Camp)",
        "Establish Work Site (Mine)",
        "Establish Work Site (Quarry)",
        "Irrigation",
        "Hire Adventurers",
        "Celebrate Holiday",
        "Relocate Capital",
        "Trade Commodities",
        "Clandestine Business",
        "Infiltration",
        "Prognostication",
        "Supernatural Solution",
        "Improve Lifestyle",
        "Creative Solution",
        "Rest and Relax (Scholarship)",
        "Request Foreign Aid",
        "Send Diplomatic Envoy",
        "Tap Treasury",
        "Capital Investment",
        "Collect Taxes",
        "Manage Trade Agreements",
        "Purchase Commodities",
        "Rest and Relax (Trade)",
        "Pledge of Fealty (Warfare)",
        "Gather Livestock",
        "Rest and Relax (Wilderness)",
        // General activity rows
        "Focused Attention",
        "Abandon Hex",
        "Build Structure",
        "Claim Hex",
        "Clear Hex",
        "Establish Settlement",
        "Establish Trade Agreement",
        "New Leadership",
        "Pledge of Fealty",
        "Quell Unrest",
        "Repair Reputation",
        "Rest and Relax",
        // Army rows
        "Recover Army",
        "Recruit Army",
        "Train Army",
        // Quell Unrest variants
        "Quell Unrest (Arts)",
        "Quell Unrest (Folklore)",
        "Quell Unrest (Intrigue)",
        "Quell Unrest (Magic)",
        "Quell Unrest (Politics)",
        "Quell Unrest (Warfare)",
        // Repair Reputation variants
        "Repair Reputation (Corruption)",
        "Repair Reputation (Decay)",
        "Repair Reputation (Strife)",
        "Repair Reputation (Crime)",
    )

    @Test
    fun allExpectedLabelsPresentInMatrixRows() {
        val rowLabels = settlementDetailsMatrixRows.map { it.label }.toSet()
        val missing = expectedLabels - rowLabels
        assertTrue(
            missing.isEmpty(),
            "Missing expected workbook labels: ${missing.sorted().joinToString(", ")}"
        )
    }

    @Test
    fun matrixRowsAreInWorkbookOrder() {
        val workbookRows = settlementDetailsMatrixRows.map { it.workbookRow }
        assertEquals(workbookRows, workbookRows.sorted(), "Rows should be in ascending workbook order")
    }

    @Test
    fun matrixRowsHaveNoBlanks() {
        settlementDetailsMatrixRows.forEach { row ->
            assertTrue(row.label.isNotBlank(), "Row ${row.workbookRow} has blank label")
        }
    }

    @Test
    fun headersHaveSkillOrAreSectionHeaders() {
        // "Any", "General", and "Army" are valid section headers without a skill
        val sectionHeadersWithoutSkill = setOf("Any", "General", "Army")
        settlementDetailsMatrixRows
            .filter { it.isHeader }
            .forEach { row ->
                assertTrue(
                    row.skill != null || row.label in sectionHeadersWithoutSkill,
                    "Header row '${row.label}' at workbook row ${row.workbookRow} should have a skill or be a known section header"
                )
            }
    }
}
