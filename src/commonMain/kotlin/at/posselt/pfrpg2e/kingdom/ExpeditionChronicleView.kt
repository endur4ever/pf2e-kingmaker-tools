package at.posselt.pfrpg2e.kingdom

/**
 * Pure grouping/formatting for the companion-expedition chronicle. Expedition results are recorded
 * (and pruned) to a chronicle but never rendered — this is the deterministic view core that turns a
 * flat, unordered entry list into newest-first per-turn groups for the display section. The sheet
 * tab/section, template, and context wiring that consumes this are the deferred jsMain work.
 *
 * See card t_994ff81a.
 */

/** One chronicle line: what a companion did on an expedition on a given turn. */
data class ChronicleEntry(
    val turn: Int,
    val companionName: String,
    val summary: String,
    /** ISO timestamp the result was applied, when present (used only for intra-turn tie-breaking). */
    val appliedAt: String? = null,
)

/** A turn's chronicle entries, grouped for the timeline display. */
data class ChronicleTurnGroup(
    val turn: Int,
    val entries: List<ChronicleEntry>,
)

/**
 * Group [entries] by turn, newest turn first, so the chronicle reads as a reverse-chronological
 * timeline. Within a turn the original relative order of the entries is preserved (a stable group),
 * so entries that arrived together stay together. An empty input yields an empty list.
 */
fun groupChronicleByTurn(entries: List<ChronicleEntry>): List<ChronicleTurnGroup> =
    entries
        .groupBy { it.turn }
        .toList()
        .sortedByDescending { (turn, _) -> turn }
        .map { (turn, turnEntries) -> ChronicleTurnGroup(turn = turn, entries = turnEntries) }

/** Total chronicle lines across all turns — for the section header / empty-state decision. */
fun chronicleEntryCount(groups: List<ChronicleTurnGroup>): Int =
    groups.sumOf { it.entries.size }
