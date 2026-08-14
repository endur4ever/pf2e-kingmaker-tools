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
        // Keyed by trimmed name but storing the group's REAL name: a group called "Pitax " and a
        // threat naming "Pitax" are the same war, but storing the trimmed form would make every
        // later exact-name lookup miss and no standing would ever move.
        val groupNames = mutableMapOf<String, String>()
        if (groups != null) {
            for (i in 0 until (groups.length as Int)) {
                val name = groups[i].name as String?
                val trimmed = name?.trim()
                if (name != null && trimmed != null && trimmed.isNotBlank()) groupNames[trimmed] = name
            }
        }
        for (i in 0 until (threats.length as Int)) {
            val threat = threats[i]
            if (threat.enemyFactionName != null) continue
            val freeText = (threat.enemyFaction as String?)?.trim()
            threat.enemyFactionName = if (freeText == null) null else groupNames[freeText]
        }
    }
}
