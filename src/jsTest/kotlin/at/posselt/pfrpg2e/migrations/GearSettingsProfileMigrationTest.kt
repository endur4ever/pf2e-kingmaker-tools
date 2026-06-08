package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.migrations.migrations.Migration36
import kotlin.test.Test
import kotlin.test.assertEquals

class GearSettingsProfileMigrationTest {
    @Test
    fun `migration 36 has expected version`() {
        assertEquals(36, Migration36().version)
    }
}
