# Implementation Plan - Multi-Scene Regional Map Support

Support custom and alternative scene maps for the Stolen Lands (such as community/Patreon maps, or duplicated scenes) by making the hardcoded scene ID for automatic hex-coordinate region mapping configurable.

## User Review Required

> [!IMPORTANT]
> Currently, the automatic tracking of camping regions based on token movement is hardcoded to a single scene ID (`AJ1k5II28u72JOmz`), which is the default Stolen Lands map from the official Paizo module. 
> 
> We propose introducing a new world-scope setting `campaignMapSceneIds` containing a comma-separated list of scene IDs. Any scene listed here will automatically use the official Kingmaker hex-to-region coordinate mapping. For other completely custom maps (e.g. Brevoy or custom regions), GMs can continue using native Foundry VTT Scene Regions to trigger region updates.

## Proposed Changes

### Settings & Configuration

#### [MODIFY] [Pfrpg2eKingdomCampingWeatherSettings.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/settings/Pfrpg2eKingdomCampingWeatherSettings.kt)
- Register `campaignMapSceneIds` as a world-scoped string setting.
- Default to `"AJ1k5II28u72JOmz"` to preserve backwards compatibility for existing campaigns using the default Paizo scene.

#### [MODIFY] [en.json](file:///home/grego/code/pf2e-kingmaker-tools/lang/en.json)
- Add translation keys:
  - `settings.campaignMapSceneIds` ("Campaign Map Scene IDs")
  - `settings.campaignMapSceneIdsHelp` ("A comma-separated list of Scene IDs that act as regional maps. Moving the party token on these scenes automatically updates the active camping region based on hex coordinates.")

### Coordinate Gating

#### [MODIFY] [CampingToken.kt](file:///home/grego/code/pf2e-kingmaker-tools/src/jsMain/kotlin/at/posselt/pfrpg2e/camping/CampingToken.kt)
- Read the configured `campaignMapSceneIds` from settings.
- Parse the comma-separated string into a set of trimmed scene IDs.
- Update `registerCampingTokenMove` to check if `game.scenes.current?.id` is within this set instead of checking a hardcoded `stolenLandsId`.

---

## Verification Plan

### Automated Tests
- Run `./gradlew jsTest -x kotlinStoreYarnLock` to verify compilation and that all existing tests pass successfully.

### Manual Verification
1. Open Module Settings and verify that the "Campaign Map Scene IDs" setting is visible with the default Paizo scene ID prefilled.
2. Verify that changing/adding scene IDs successfully enables auto-region mapping on those scenes.
