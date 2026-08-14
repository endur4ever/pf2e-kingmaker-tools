package at.posselt.pfrpg2e.migrations

import at.posselt.pfrpg2e.camping.CampingActor
import at.posselt.pfrpg2e.camping.getCamping
import at.posselt.pfrpg2e.camping.getCampingActors
import at.posselt.pfrpg2e.camping.setCamping
import at.posselt.pfrpg2e.kingdom.KingdomActor
import at.posselt.pfrpg2e.kingdom.getKingdom
import at.posselt.pfrpg2e.kingdom.getKingdomActors
import at.posselt.pfrpg2e.kingdom.setKingdom
import at.posselt.pfrpg2e.migrations.migrations.Migration17
import at.posselt.pfrpg2e.migrations.migrations.Migration18
import at.posselt.pfrpg2e.migrations.migrations.Migration19
import at.posselt.pfrpg2e.migrations.migrations.Migration20
import at.posselt.pfrpg2e.migrations.migrations.Migration21
import at.posselt.pfrpg2e.migrations.migrations.Migration22
import at.posselt.pfrpg2e.migrations.migrations.Migration23
import at.posselt.pfrpg2e.migrations.migrations.Migration24
import at.posselt.pfrpg2e.migrations.migrations.Migration25
import at.posselt.pfrpg2e.migrations.migrations.Migration26
import at.posselt.pfrpg2e.migrations.migrations.Migration27
import at.posselt.pfrpg2e.migrations.migrations.Migration28
import at.posselt.pfrpg2e.migrations.migrations.Migration29
import at.posselt.pfrpg2e.migrations.migrations.Migration30
import at.posselt.pfrpg2e.migrations.migrations.Migration31
import at.posselt.pfrpg2e.migrations.migrations.Migration32
import at.posselt.pfrpg2e.migrations.migrations.Migration33
import at.posselt.pfrpg2e.migrations.migrations.Migration34
import at.posselt.pfrpg2e.migrations.migrations.Migration35
import at.posselt.pfrpg2e.migrations.migrations.Migration36
import at.posselt.pfrpg2e.migrations.migrations.Migration37
import at.posselt.pfrpg2e.migrations.migrations.Migration38
import at.posselt.pfrpg2e.migrations.migrations.Migration39
import at.posselt.pfrpg2e.migrations.migrations.Migration40
import at.posselt.pfrpg2e.migrations.migrations.Migration41
import at.posselt.pfrpg2e.migrations.migrations.Migration42
import at.posselt.pfrpg2e.migrations.migrations.Migration43
import at.posselt.pfrpg2e.migrations.migrations.Migration44
import at.posselt.pfrpg2e.migrations.migrations.Migration45
import at.posselt.pfrpg2e.migrations.migrations.Migration46
import at.posselt.pfrpg2e.migrations.migrations.Migration47
import at.posselt.pfrpg2e.migrations.migrations.Migration48
import at.posselt.pfrpg2e.migrations.migrations.Migration49
import at.posselt.pfrpg2e.migrations.migrations.Migration50
import at.posselt.pfrpg2e.migrations.migrations.Migration51
import at.posselt.pfrpg2e.settings.pfrpg2eKingdomCampingWeather
import at.posselt.pfrpg2e.utils.isFirstGM
import at.posselt.pfrpg2e.utils.openJournal
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.toRecord
import com.foundryvtt.core.Game
import com.foundryvtt.core.ui
import js.objects.recordOf

private suspend fun createBackups(
    game: Game,
    kingdomActors: List<KingdomActor>,
    campingActors: List<CampingActor>,
    currentVersion: Int
) {
    val backup = recordOf(
        "version" to currentVersion,
        "camping" to campingActors.map {
            it.id!! to it.getCamping()
        }.toRecord(),
        "kingdoms" to kingdomActors.map {
            it.id!! to it.getKingdom()
        }.toRecord()
    )
    game.settings.pfrpg2eKingdomCampingWeather.setLatestMigrationBackup(
        JSON.stringify(backup)
    )
}

internal val migrations = listOf(
    Migration17(),
    Migration18(),
    Migration19(),
    Migration20(),
    Migration21(),
    Migration22(),
    Migration23(),
    Migration24(),
    Migration25(),
    Migration26(),
    Migration27(),
    Migration28(),
    Migration29(),
    Migration30(),
    Migration31(),
    Migration32(),
    Migration33(),
    Migration34(),
    Migration35(),
    Migration36(),
    Migration37(),
    Migration38(),
    Migration39(),
    Migration40(),
    // Migrations 41-48 were authored as classes but never registered here, so they never ran on
    // existing worlds — new fields (accessGrants, bankedBonuses, autoSucceedInClaimedHexes,
    // companion session ids, etc.) were left un-backfilled. Registered so the multi-version upgrade
    // path actually applies them. MigrationChainTest guards contiguity so this can't regress.
    Migration41(),
    Migration42(),
    Migration43(),
    Migration44(),
    Migration45(),
    Migration46(),
    Migration47(),
    Migration48(),
    Migration49(),
    Migration50(),
    Migration51(),
)

private val latestMigrationVersion = migrations.maxOfOrNull { it.version }!!

/** The current (latest) schema version this module migrates to. */
fun currentSchemaVersion(): Int = latestMigrationVersion

/**
 * Run the kingdom migration chain on STANDALONE data (e.g. an imported JSON payload) — applies every
 * migration newer than [fromVersion] in order, mutating [kingdom] in place. Reuses the exact same
 * migration list the world-level flow uses, without touching world state or the schemaVersion
 * setting. Callers must have already established that [fromVersion] is at/above the oldest supported
 * version (see [OLDEST_SUPPORTED_SCHEMA_VERSION]).
 */
suspend fun Game.migrateKingdomDataFrom(fromVersion: Int, kingdom: dynamic) {
    migrations.filter { it.version > fromVersion }.forEach { it.migrateKingdom(this, kingdom) }
}

/** Camping counterpart of [migrateKingdomDataFrom]. */
suspend fun Game.migrateCampingDataFrom(fromVersion: Int, camping: dynamic) {
    migrations.filter { it.version > fromVersion }.forEach { it.migrateCamping(this, camping) }
}

suspend fun Game.fixSchemaVersionV13() {
    val schemaVersion = settings.pfrpg2eKingdomCampingWeather.getSchemaVersion()
    val hasKingdomData = getKingdomActors().isNotEmpty()
    val hasCampingData = getCampingActors().isNotEmpty()
    if (schemaVersion == 0 && (hasCampingData || hasKingdomData)) {
        settings.pfrpg2eKingdomCampingWeather.setSchemaVersion(18)
    }
}

suspend fun Game.migratePfrpg2eKingdomCampingWeather() {
    if (isFirstGM()) {
        fixSchemaVersionV13()
        val currentVersion = settings.pfrpg2eKingdomCampingWeather.getSchemaVersion()
            .takeIf { it != 0 }
            ?: latestMigrationVersion
        settings.pfrpg2eKingdomCampingWeather.setSchemaVersion(currentVersion)
        if (currentVersion < 16) {
            ui.notifications.error(
                "${t("moduleName")}: ${
                    t(
                        "migrations.unsupportedVersions",
                        recordOf("version" to "4.8.2 (FoundryVTT V12)")
                    )
                }"
            )
        } else if (currentVersion < latestMigrationVersion) {
            console.log(
                "${t("moduleName")}: ${
                    t(
                        "migrations.upgradingFromTo",
                        recordOf("fromVersion" to currentVersion, "toVersion" to latestMigrationVersion)
                    )
                }"
            )
            try {
                migrateFrom(currentVersion)
            } catch (e: Throwable) {
                console.log(e)
                ui.notifications.error(t("migrations.failed", recordOf("version" to currentVersion)))
                throw e
            }
        }
    }
}

private suspend fun Game.migrateFrom(currentVersion: Int) {
    ui.notifications.info("${t("moduleName")}: ${t("migrations.doNotClose")}")
    // create backups
    val kingdomActors = getKingdomActors()
    val campingActors = getCampingActors()
    createBackups(this, kingdomActors, campingActors, currentVersion)

    val migrationsToRun = migrations.filter { it.version > currentVersion }

    migrationsToRun
        .forEach { migration ->
            ui.notifications.info(
                "${t("moduleName")}: ${
                    t(
                        "migrations.runningMigration",
                        recordOf("version" to migration.version)
                    )
                }"
            )
            campingActors.forEach { actor ->
                actor.getCamping()?.let { camping ->
                    migration.migrateCamping(this, camping)
                    actor.setCamping(camping)
                }
            }

            kingdomActors.forEach { actor ->
                actor.getKingdom()?.let { kingdom ->
                    migration.migrateKingdom(this, kingdom)
                    actor.setKingdom(kingdom)
                }
            }

            migration.migrateOther(this)
        }

    settings.pfrpg2eKingdomCampingWeather.setSchemaVersion(latestMigrationVersion)
    ui.notifications.info("${t("moduleName")}: ${t("migrations.successful")}")

    if (migrationsToRun.any { it.showUpgradingNotices }) {
        openJournal("Compendium.pf2e-kingmaker-tools.kingmaker-tools-journals.JournalEntry.wz1mIWMxDJVsMIUd")
    }
}

