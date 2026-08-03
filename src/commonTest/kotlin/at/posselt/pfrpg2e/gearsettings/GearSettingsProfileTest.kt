package at.posselt.pfrpg2e.gearsettings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class GearSettingsProfileTest {
    @Test
    fun `raw profile has expected defaults`() {
        val raw = GearSettingsProfile.raw()
        assertEquals("raw", raw.id)
        assertEquals("RAW (Rules as Written)", raw.name)
        assertTrue(raw.isBuiltin)
        assertEquals(1, raw.schemaVersion)
        // Kingdom defaults
        assertEquals("xp", raw.settings.kingdom.advancement)
        assertEquals(10, raw.settings.kingdom.ruinThreshold)
        assertEquals(15, raw.settings.kingdom.eventDc)
        assertEquals(6, raw.settings.kingdom.leadershipActivityCap)
        assertEquals(8, raw.settings.kingdom.leadershipActivityCapWithTownhall)
        assertFalse(raw.settings.kingdom.canUpgradeNonCapital)
        // Camping defaults
        assertFalse(raw.settings.camping.campingActivityCountByPartySize)
        assertTrue(raw.settings.camping.enableWeather)
        // Hex defaults
        assertEquals(0, raw.settings.hex.travelCostRiverNoBridgeAdditional)
        assertTrue(raw.settings.hex.hexMapEnabled)
        // Army defaults
        assertTrue(raw.settings.army.enableCombatTracks)
        // UI defaults
        assertFalse(raw.settings.ui.hideBuiltinKingdomSheet)
        assertFalse(raw.settings.ui.disableFirstRunMessage)
    }

    @Test
    fun `vance kerenshara profile has expected values`() {
        val vk = GearSettingsProfile.vanceKerenshara()
        assertEquals("vance-kerenshara", vk.id)
        assertEquals("Vance & Kerenshara", vk.name)
        assertTrue(vk.isBuiltin)
        assertEquals("Vance & Kerenshara", vk.author)
    }

    @Test
    fun `gregory profile has custom values`() {
        val greg = GearSettingsProfile.gregory()
        assertEquals("gregory", greg.id)
        assertEquals("Gregory's Gear Settings", greg.name)
        assertFalse(greg.isBuiltin)
        assertEquals("Gregory", greg.author)
        // Kingdom overrides
        assertEquals(5, greg.settings.kingdom.ruinThreshold)
        assertEquals(5, greg.settings.kingdom.eventDc)
        assertEquals(8, greg.settings.kingdom.leadershipActivityCap)
        assertEquals(12, greg.settings.kingdom.leadershipActivityCapWithTownhall)
        assertTrue(greg.settings.kingdom.canUpgradeNonCapital)
        assertTrue(greg.settings.kingdom.capitalCanGrowOneSizeLarger)
        assertTrue(greg.settings.kingdom.noRandomCombatInClaimedHexes)
        assertTrue(greg.settings.kingdom.cultOfTheBloomEvents)
        assertEquals(1, greg.settings.kingdom.settlementInfluenceRadius)
        // Camping overrides
        assertTrue(greg.settings.camping.campingActivityCountByPartySize)
        assertFalse(greg.settings.camping.enableWeather)
        // Hex overrides
        assertEquals(1, greg.settings.hex.travelCostRiverNoBridgeAdditional)
        assertTrue(greg.settings.hex.pavedStreetsReduceTravelCost)
        // UI overrides
        assertTrue(greg.settings.ui.hideBuiltinKingdomSheet)
        assertTrue(greg.settings.ui.disableFirstRunMessage)
    }

    @Test
    fun `registry serialization round-trip`() {
        val registry = GearSettingsProfileRegistry(
            activeProfileId = "raw",
            profiles = listOf(GearSettingsProfile.raw(), GearSettingsProfile.vanceKerenshara()),
        )
        val json = registry.toJson()
        assertNotNull(json)
        val restored = GearSettingsProfileRegistry.fromJson(json)
        assertNotNull(restored)
        assertEquals("raw", restored!!.activeProfileId)
        assertEquals(2, restored.profiles.size)
        assertEquals("raw", restored.profiles[0].id)
        assertEquals("vance-kerenshara", restored.profiles[1].id)
    }

    @Test
    fun `registry fromJson handles invalid input`() {
        assertNull(GearSettingsProfileRegistry.fromJson("not json"))
        assertNull(GearSettingsProfileRegistry.fromJson(""))
    }
}

class GearSettingsResolutionHelperTest {
    @Test
    fun `defaults when profile is null`() {
        // Kingdom
        assertEquals("xp", GearSettingsResolutionHelper.getAdvancement(null))
        assertEquals(10, GearSettingsResolutionHelper.getRuinThreshold(null))
        assertEquals(15, GearSettingsResolutionHelper.getEventDc(null))
        assertEquals(0, GearSettingsResolutionHelper.getEventDcStep(null))
        assertEquals(6, GearSettingsResolutionHelper.getLeadershipActivityCap(null, false))
        assertEquals(8, GearSettingsResolutionHelper.getLeadershipActivityCap(null, true))
        assertFalse(GearSettingsResolutionHelper.canUpgradeNonCapitalSettlement(null))
        assertFalse(GearSettingsResolutionHelper.canCapitalGrowOneSizeLarger(null))
        assertFalse(GearSettingsResolutionHelper.isRandomCombatSuppressedInClaimedHexes(null))
        assertFalse(GearSettingsResolutionHelper.isStructureBonusCappedAtKingdomLevel(null))
        assertEquals(0, GearSettingsResolutionHelper.getSettlementInfluenceRadius(null))
        assertFalse(GearSettingsResolutionHelper.getCultOfTheBloomEvents(null))
        // Camping
        assertEquals(4, GearSettingsResolutionHelper.getCampingActivityCount(null, 5))
        assertFalse(GearSettingsResolutionHelper.isShelteredEnabled(null))
        assertTrue(GearSettingsResolutionHelper.isWeatherEnabled(null))
        // Hex
        assertEquals(0, GearSettingsResolutionHelper.getTravelCostRiverNoBridgeAdditional(null))
        assertFalse(GearSettingsResolutionHelper.doPavedStreetsReduceTravelCost(null))
        assertTrue(GearSettingsResolutionHelper.isHexMapEnabled(null))
        // Army
        assertTrue(GearSettingsResolutionHelper.isCombatTracksEnabled(null))
        // UI
        assertFalse(GearSettingsResolutionHelper.shouldHideBuiltinKingdomSheet(null))
        assertTrue(GearSettingsResolutionHelper.isPartyActorIconsEnabled(null))
        assertTrue(GearSettingsResolutionHelper.isWeatherSoundFxEnabled(null))
        assertTrue(GearSettingsResolutionHelper.isTokenMappingEnabled(null))
        assertFalse(GearSettingsResolutionHelper.isFirstRunMessageDisabled(null))
        // Profile resolution
        assertNull(GearSettingsResolutionHelper.getActiveProfile(null))
    }

    @Test
    fun `gregory profile values via resolution helper`() {
        val greg = GearSettingsProfile.gregory()
        // Kingdom
        assertEquals(5, GearSettingsResolutionHelper.getRuinThreshold(greg))
        assertEquals(5, GearSettingsResolutionHelper.getEventDc(greg))
        assertEquals(8, GearSettingsResolutionHelper.getLeadershipActivityCap(greg, false))
        assertEquals(12, GearSettingsResolutionHelper.getLeadershipActivityCap(greg, true))
        assertTrue(GearSettingsResolutionHelper.canUpgradeNonCapitalSettlement(greg))
        assertTrue(GearSettingsResolutionHelper.canCapitalGrowOneSizeLarger(greg))
        assertTrue(GearSettingsResolutionHelper.isRandomCombatSuppressedInClaimedHexes(greg))
        assertTrue(GearSettingsResolutionHelper.isStructureBonusCappedAtKingdomLevel(greg))
        assertEquals(1, GearSettingsResolutionHelper.getSettlementInfluenceRadius(greg))
        assertTrue(GearSettingsResolutionHelper.getCultOfTheBloomEvents(greg))
        // Camping
        assertEquals(7, GearSettingsResolutionHelper.getCampingActivityCount(greg, 7))
        assertFalse(GearSettingsResolutionHelper.isWeatherEnabled(greg))
        // Hex
        assertEquals(1, GearSettingsResolutionHelper.getTravelCostRiverNoBridgeAdditional(greg))
        assertTrue(GearSettingsResolutionHelper.doPavedStreetsReduceTravelCost(greg))
        // UI
        assertTrue(GearSettingsResolutionHelper.shouldHideBuiltinKingdomSheet(greg))
        assertTrue(GearSettingsResolutionHelper.isFirstRunMessageDisabled(greg))
    }

    @Test
    fun `active profile resolution in registry`() {
        val raw = GearSettingsProfile.raw()
        val greg = GearSettingsProfile.gregory()
        val registry = GearSettingsProfileRegistry(
            activeProfileId = "gregory",
            profiles = listOf(raw, greg),
        )
        val active = GearSettingsResolutionHelper.getActiveProfile(registry)
        assertEquals(greg.id, active?.id)
        assertEquals(5, active?.settings?.kingdom?.ruinThreshold)

        // When no active profile
        val registryNone = GearSettingsProfileRegistry(
            activeProfileId = null,
            profiles = listOf(raw, greg),
        )
        assertNull(GearSettingsResolutionHelper.getActiveProfile(registryNone))
    }

    @Test
    fun `raw profile values via resolution helper match defaults`() {
        val raw = GearSettingsProfile.raw()
        assertEquals(10, GearSettingsResolutionHelper.getRuinThreshold(raw))
        assertEquals(15, GearSettingsResolutionHelper.getEventDc(raw))
        assertEquals(6, GearSettingsResolutionHelper.getLeadershipActivityCap(raw, false))
        assertEquals(8, GearSettingsResolutionHelper.getLeadershipActivityCap(raw, true))
        assertEquals(4, GearSettingsResolutionHelper.getCampingActivityCount(raw, 7))
        assertTrue(GearSettingsResolutionHelper.isWeatherEnabled(raw))
        assertTrue(GearSettingsResolutionHelper.isHexMapEnabled(raw))
    }
}

class GearSettingsProfileImportExportTest {
    @Test
    fun `exported profile round-trips through validation`() {
        val exported = GearSettingsProfileImportExport.exportProfile(GearSettingsProfile.gregory())
        assertTrue(exported.contains("\"schemaVersion\": 1"))
        assertTrue(exported.contains("\"settings\""))

        when (val parsed = GearSettingsProfileImportExport.parseAndValidateProfile(exported)) {
            is GearSettingsProfileImport.Valid -> {
                assertEquals("gregory", parsed.profile.id)
                assertEquals(5, parsed.profile.settings.kingdom.ruinThreshold)
                assertTrue(parsed.profile.settings.hex.pavedStreetsReduceTravelCost)
            }
            is GearSettingsProfileImport.Invalid -> fail(parsed.message)
        }
    }

    @Test
    fun `import rejects invalid JSON`() {
        when (val parsed = GearSettingsProfileImportExport.parseAndValidateProfile("not json")) {
            is GearSettingsProfileImport.Invalid -> assertEquals("Invalid profile: not valid JSON", parsed.message)
            is GearSettingsProfileImport.Valid -> fail("Expected invalid profile, got ${parsed.profile}")
        }
    }

    @Test
    fun `import rejects missing required field`() {
        val json = GearSettingsProfileImportExport.exportProfile(GearSettingsProfile.gregory())
            .replace("\"schemaVersion\": 1,\n", "")
        when (val parsed = GearSettingsProfileImportExport.parseAndValidateProfile(json)) {
            is GearSettingsProfileImport.Invalid -> assertEquals("Invalid profile: missing 'schemaVersion'", parsed.message)
            is GearSettingsProfileImport.Valid -> fail("Expected invalid profile, got ${parsed.profile}")
        }
    }

    @Test
    fun `import rejects unknown nested setting key`() {
        val json = GearSettingsProfileImportExport.exportProfile(GearSettingsProfile.gregory())
            .replace("\"ruinThreshold\": 5", "\"ruinThreshold\": 5,\n      \"mysterySetting\": true")
        when (val parsed = GearSettingsProfileImportExport.parseAndValidateProfile(json)) {
            is GearSettingsProfileImport.Invalid -> assertEquals("Invalid profile: unknown setting key 'kingdom.mysterySetting'", parsed.message)
            is GearSettingsProfileImport.Valid -> fail("Expected invalid profile, got ${parsed.profile}")
        }
    }

    @Test
    fun `import rejects integer values outside allowed range`() {
        val json = GearSettingsProfileImportExport.exportProfile(GearSettingsProfile.gregory())
            .replace("\"ruinThreshold\": 5", "\"ruinThreshold\": 0")
        when (val parsed = GearSettingsProfileImportExport.parseAndValidateProfile(json)) {
            is GearSettingsProfileImport.Invalid -> assertEquals("Invalid profile: 'kingdom.ruinThreshold' value 0 out of range [1, 20]", parsed.message)
            is GearSettingsProfileImport.Valid -> fail("Expected invalid profile, got ${parsed.profile}")
        }
    }

    @Test
    fun `import rejects future schema version`() {
        val json = GearSettingsProfileImportExport.exportProfile(GearSettingsProfile.gregory())
            .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        when (val parsed = GearSettingsProfileImportExport.parseAndValidateProfile(json)) {
            is GearSettingsProfileImport.Invalid -> assertEquals("Invalid profile: version 99 newer than module-supported 1", parsed.message)
            is GearSettingsProfileImport.Valid -> fail("Expected invalid profile, got ${parsed.profile}")
        }
    }

    @Test
    fun `registry import renames colliding profile and activates it`() {
        val registry = GearSettingsProfileRegistry(
            activeProfileId = "raw",
            profiles = listOf(GearSettingsProfile.raw(), GearSettingsProfile.vanceKerenshara(), GearSettingsProfile.gregory()),
        )
        val json = GearSettingsProfileImportExport.exportProfile(GearSettingsProfile.gregory())

        when (val result = GearSettingsProfileImportExport.importProfile(registry, json)) {
            is GearSettingsProfileRegistryImport.Valid -> {
                assertEquals("gregorys-gear-settings", result.importedProfile.id)
                assertEquals(result.importedProfile.id, result.registry.activeProfileId)
                assertFalse(result.importedProfile.isBuiltin)
                assertNotNull(result.registry.profiles.firstOrNull { it.id == "raw" })
                assertNotNull(result.registry.profiles.firstOrNull { it.id == "vance-kerenshara" })
            }
            is GearSettingsProfileRegistryImport.Invalid -> fail(result.message)
        }
    }

    @Test
    fun `first run seeding imports current settings once`() {
        val seeded = GearSettingsProfileImportExport.seedRegistryFromCurrentSettings(
            registry = GearSettingsProfileRegistry(profiles = emptyList()),
            currentSettings = GearSettingsProfile.gregory().settings,
            createdAt = "2026-06-07T00:00:00Z",
        )
        assertEquals("gregory", seeded.activeProfileId)
        assertNotNull(seeded.profiles.firstOrNull { it.id == "gregory" })

        val reseeded = GearSettingsProfileImportExport.seedRegistryFromCurrentSettings(
            registry = seeded,
            currentSettings = GearSettings(),
            createdAt = "2026-06-08T00:00:00Z",
        )
        assertEquals(seeded, reseeded)
    }
}
