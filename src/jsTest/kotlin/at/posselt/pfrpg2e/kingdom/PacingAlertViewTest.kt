package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.data.PacingAlertSeverity
import at.posselt.pfrpg2e.kingdom.data.PacingAlertType
import at.posselt.pfrpg2e.kingdom.data.RawPacingAlert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PacingAlertViewTest {
    private fun alert(
        id: String,
        type: PacingAlertType,
        severity: PacingAlertSeverity,
        turnCreated: Int,
    ): RawPacingAlert = RawPacingAlert(
        id = id,
        type = type.value,
        severity = severity.value,
        message = type.i18nKey,
        turnCreated = turnCreated,
        relatedEntityId = null,
    )

    @Test
    fun nullHistoryProducesEmptyView() {
        val view = buildPacingAlertView(null)
        assertFalse(view.hasAlerts)
        assertEquals(0, view.warningCount)
        assertEquals(0, view.criticalCount)
        assertTrue(view.alerts.isEmpty())
    }

    @Test
    fun emptyHistoryProducesEmptyView() {
        val view = buildPacingAlertView(emptyArray())
        assertFalse(view.hasAlerts)
        assertTrue(view.alerts.isEmpty())
    }

    @Test
    fun ordersNewestFirstByTurnCreated() {
        val view = buildPacingAlertView(
            arrayOf(
                alert("a", PacingAlertType.STAGNATION, PacingAlertSeverity.WARNING, turnCreated = 10),
                alert("b", PacingAlertType.STAGNATION, PacingAlertSeverity.CRITICAL, turnCreated = 20),
                alert("c", PacingAlertType.LEVEL_MISMATCH, PacingAlertSeverity.WARNING, turnCreated = 5),
            )
        )
        assertEquals(listOf("b", "a", "c"), view.alerts.map { it.id })
    }

    @Test
    fun countsSeveritiesAndFlagsCritical() {
        val view = buildPacingAlertView(
            arrayOf(
                alert("a", PacingAlertType.STAGNATION, PacingAlertSeverity.WARNING, turnCreated = 1),
                alert("b", PacingAlertType.STAGNATION, PacingAlertSeverity.CRITICAL, turnCreated = 2),
                alert("c", PacingAlertType.TURN_GAP, PacingAlertSeverity.WARNING, turnCreated = 3),
            )
        )
        assertTrue(view.hasAlerts)
        assertEquals(2, view.warningCount)
        assertEquals(1, view.criticalCount)
        assertEquals(true, view.alerts.first { it.id == "b" }.isCritical)
        assertEquals(false, view.alerts.first { it.id == "a" }.isCritical)
    }

    @Test
    fun preservesMessageKeyForLocalization() {
        val view = buildPacingAlertView(
            arrayOf(alert("a", PacingAlertType.STAGNATION, PacingAlertSeverity.WARNING, turnCreated = 1))
        )
        assertEquals(PacingAlertType.STAGNATION.i18nKey, view.alerts.single().messageKey)
    }
}
