package at.posselt.pfrpg2e.migrations.migrations

import at.posselt.pfrpg2e.data.kingdom.initialGoalForFaction
import at.posselt.pfrpg2e.data.kingdom.pickArchetypeForFaction
import at.posselt.pfrpg2e.kingdom.KingdomData
import at.posselt.pfrpg2e.kingdom.data.RawFactionAgenda
import at.posselt.pfrpg2e.kingdom.factionAgendaArchetypesById
import com.foundryvtt.core.Game
import js.objects.recordOf

/**
 * Faction agenda backfill (docs/plans/2026-07-09-plan-faction-agenda.md section 2.3; the plan's
 * "MigrationNN" resolves to 72, the contiguous next).
 *
 * Existence-checked per group, never assigned over: a re-run keeps every agenda the GM has
 * adjusted in between, and only the deterministic name hash decides archetype and first goal,
 * so two runs on the same world can never disagree.
 */
class Migration72 : Migration(72) {
    override suspend fun migrateKingdom(game: Game, kingdom: KingdomData) {
        val groups = kingdom.groups ?: return
        val archetypes = factionAgendaArchetypesById()
        kingdom.groups = groups.map { group ->
            if (group.agenda != null) return@map group
            val archetype = pickArchetypeForFaction(group.name)
            val pool = archetypes[archetype]?.goals?.toList() ?: emptyList()
            group.agenda = RawFactionAgenda(
                goalId = initialGoalForFaction(group.name, pool) ?: "expand",
                goalTitle = "",
                progress = 0,
                segments = 6,
                archetype = archetype,
                moveCooldowns = recordOf(),
                lastAdvancedTurn = null,
                targetFaction = null,
            )
            group
        }.toTypedArray()
    }
}
