package at.posselt.pfrpg2e.migrations.migrations

import com.foundryvtt.core.Game

/**
 * Migration 52 — link war threats to factions.
 *
 * Backfills [at.posselt.pfrpg2e.kingdom.data.RawWarThreat.enemyFactionName]. Threats whose
 * free-text `enemyFaction` already spells an existing group's name are linked automatically, so a
 * campaign that has been naming its enemies consistently gets its war-end conditions, standing
 * shifts and peace offers without the GM re-entering anything. Everything else is set to null,
 * which reads as "not a faction's war".
 *
 * Matching is exact after trimming: a fuzzy match here would silently bind a war to the wrong
 * faction, and the dropdown makes fixing an unlinked threat a single click.
 */
class Migration52 : Migration(52) {

    override suspend fun migrateKingdom(game: Game, kingdom: dynamic) {
        val threats = kingdom.warThreats ?: return
        val groups = kingdom.groups
        val groupNames = mutableSetOf<String>()
        if (groups != null) {
            for (i in 0 until (groups.length as Int)) {
                (groups[i].name as String?)?.trim()?.takeIf { it.isNotBlank() }?.let(groupNames::add)
            }
        }
        for (i in 0 until (threats.length as Int)) {
            val threat = threats[i]
            if (threat.enemyFactionName != null) continue
            val freeText = (threat.enemyFaction as String?)?.trim()
            threat.enemyFactionName = if (freeText != null && freeText in groupNames) freeText else null
        }
    }
}
