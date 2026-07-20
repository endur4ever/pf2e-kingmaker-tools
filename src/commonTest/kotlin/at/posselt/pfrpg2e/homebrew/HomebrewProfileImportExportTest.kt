package at.posselt.pfrpg2e.homebrew

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomebrewProfileImportExportTest {
    private fun profile(id: String, name: String = "My House Rules") = HomebrewRulesProfile(
        id = id,
        name = name,
        version = 1,
        isActive = false,
        createdAt = "2026-01-01T00:00:00.000Z",
        updatedAt = "2026-01-02T00:00:00.000Z",
        description = "shared rules",
        rules = HomebrewRules.gregory(),
    )

    @Test
    fun exportThenImportReproducesTheProfileExactly() {
        val original = profile("original")
        val json = HomebrewProfileImportExport.exportProfile(original)
        val parsed = HomebrewProfileImportExport.parseImport(json)
        assertTrue(parsed is HomebrewProfileImport.Valid)
        val round = (parsed as HomebrewProfileImport.Valid).profiles.single()
        // Everything but the id (reassigned on merge) survives the round-trip.
        assertEquals(original.name, round.name)
        assertEquals(original.description, round.description)
        assertEquals(original.rules, round.rules)
    }

    @Test
    fun importIntoAddsAndActivatesTheProfile() {
        val json = HomebrewProfileImportExport.exportProfile(profile("original", "Grim Rules"))
        val result = HomebrewProfileImportExport.importInto(HomebrewProfileRegistry(), json, activate = true)
        assertTrue(result is HomebrewRegistryImport.Valid)
        val reg = (result as HomebrewRegistryImport.Valid).registry
        assertEquals(1, reg.profiles.size)
        assertEquals(reg.profiles.single().id, reg.activeProfileId)
        assertTrue(reg.profiles.single().isActive)
        assertEquals("grim-rules", reg.profiles.single().id)  // slugified name
    }

    @Test
    fun importAssignsCollisionFreeIds() {
        val existing = HomebrewProfileRegistry(profiles = listOf(profile("grim-rules", "Grim Rules")))
        val json = HomebrewProfileImportExport.exportProfile(profile("whatever", "Grim Rules"))
        val result = HomebrewProfileImportExport.importInto(existing, json) as HomebrewRegistryImport.Valid
        assertEquals(2, result.registry.profiles.size)
        assertEquals("grim-rules-1", result.imported.single().id)  // suffixed to avoid collision
    }

    @Test
    fun newerSchemaVersionIsRejected() {
        val json = """{"schemaVersion": 2, "profiles": []}"""
        val parsed = HomebrewProfileImportExport.parseImport(json)
        assertTrue(parsed is HomebrewProfileImport.Invalid)
    }

    @Test
    fun emptyOrMalformedPayloadsAreRejected() {
        assertTrue(HomebrewProfileImportExport.parseImport("not json") is HomebrewProfileImport.Invalid)
        assertTrue(HomebrewProfileImportExport.parseImport("""{"schemaVersion": 1, "profiles": []}""") is HomebrewProfileImport.Invalid)
        // Missing a required profile field (name/createdAt/rules) fails validation, not a throw.
        val missingRequired = """{"schemaVersion": 1, "profiles": [{"id": "x"}]}"""
        assertTrue(HomebrewProfileImportExport.parseImport(missingRequired) is HomebrewProfileImport.Invalid)
    }

    @Test
    fun unknownFieldsAreDroppedAndOptionalFieldsDefault() {
        // An older/newer profile with an unknown field and no description: unknown dropped, description defaults null.
        val json = """
            {"schemaVersion": 1, "profiles": [
              {"id": "x", "name": "Legacy", "createdAt": "2026-01-01T00:00:00Z",
               "updatedAt": "2026-01-01T00:00:00Z", "rules": {}, "somethingRemoved": true}
            ]}
        """.trimIndent()
        val parsed = HomebrewProfileImportExport.parseImport(json)
        assertTrue(parsed is HomebrewProfileImport.Valid)
        val p = (parsed as HomebrewProfileImport.Valid).profiles.single()
        assertEquals("Legacy", p.name)
        assertNull(p.description)                       // optional defaulted
        assertEquals(HomebrewRules(), p.rules)          // empty rules object -> all defaults
    }

    @Test
    fun exportAllRoundTripsEveryProfile() {
        val profiles = listOf(profile("a", "Alpha"), profile("b", "Beta"))
        val json = HomebrewProfileImportExport.exportAll(profiles)
        val parsed = HomebrewProfileImportExport.parseImport(json) as HomebrewProfileImport.Valid
        assertEquals(listOf("Alpha", "Beta"), parsed.profiles.map { it.name })
    }
}
