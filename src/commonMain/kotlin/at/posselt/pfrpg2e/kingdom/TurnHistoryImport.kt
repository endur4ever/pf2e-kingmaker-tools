package at.posselt.pfrpg2e.kingdom

/**
 * A single turn row parsed from a pasted workbook (Google Sheets History / Turn-Tracker) export.
 * Platform-neutral: the Foundry dialog maps these onto RawTurnRecord before merging into history.
 * Only [turn] is required; every other field defaults so a sparse workbook still imports.
 */
data class ParsedTurnRow(
    val turn: Int,
    val fame: Int = 0,
    val resourcePoints: Int = 0,
    val consumption: Int = 0,
    val unrest: Int = 0,
    val xpAwarded: Int? = null,
    val notes: String? = null,
)

/** A per-row parse failure, carrying the 1-based row number (as the GM sees it) and a message. */
data class TurnRowError(val rowNumber: Int, val message: String)

data class TurnRowsParseResult(
    val records: List<ParsedTurnRow>,
    val errors: List<TurnRowError>,
)

/** The RawTurnRecord fields the importer understands, in the sensible default workbook order. */
val TURN_ROW_FIELDS = listOf("turn", "fame", "resourcePoints", "consumption", "unrest", "xpAwarded", "notes")

private val headerTokens = mapOf(
    "turn" to "turn", "round" to "turn",
    "fame" to "fame", "famepoints" to "fame",
    "rp" to "resourcePoints", "resourcepoints" to "resourcePoints", "resources" to "resourcePoints",
    "consumption" to "consumption", "food" to "consumption",
    "unrest" to "unrest",
    "xp" to "xpAwarded", "xpawarded" to "xpAwarded", "experience" to "xpAwarded",
    "notes" to "notes", "note" to "notes", "comment" to "notes", "comments" to "notes",
)

private fun splitCells(line: String): List<String> =
    (if ('\t' in line) line.split('\t') else line.split(',')).map { it.trim() }

/** Normalize the numeric-looking header keys to canonical field names; null if not a header. */
private fun detectHeaderMapping(cells: List<String>): Map<String, Int>? {
    val mapping = mutableMapOf<String, Int>()
    var matched = 0
    cells.forEachIndexed { index, cell ->
        val key = cell.lowercase().replace(Regex("[^a-z]"), "")
        val field = headerTokens[key]
        if (field != null && field !in mapping) {
            mapping[field] = index
            matched++
        }
    }
    // A header must resolve at least a "turn" column plus one more, else it's probably data.
    return if ("turn" in mapping && matched >= 2) mapping else null
}

/** Parse an int cell tolerant of blank cells, whitespace, thousands separators and a leading '+'. */
private fun parseIntCell(cell: String): Int? {
    val cleaned = cell.trim().removePrefix("+").replace(" ", "").replace("_", "")
        // Strip thousands separators only when they're clearly grouping (1,000 / 1.000.000), never a decimal.
        .let { if (it.count { c -> c == ',' } >= 1 && !it.contains('.')) it.replace(",", "") else it }
    if (cleaned.isEmpty()) return null
    return cleaned.toIntOrNull()
}

/**
 * Parse pasted TSV/CSV workbook rows into [ParsedTurnRow]s, collecting per-row errors rather than
 * throwing. A leading header line is auto-detected and, if present, overrides [defaultMapping].
 * Blank lines are skipped (and don't count toward row numbers the GM sees for data rows... actually
 * row numbers are the physical line numbers so they line up with the paste). Rows missing a valid
 * integer `turn` are reported as errors and dropped; unmapped optional fields default.
 *
 * @param text the pasted block
 * @param defaultMapping field-name -> 0-based column index, used when no header line is detected
 */
fun parseTurnRows(
    text: String,
    defaultMapping: Map<String, Int> = TURN_ROW_FIELDS.withIndex().associate { (i, f) -> f to i },
): TurnRowsParseResult {
    val records = mutableListOf<ParsedTurnRow>()
    val errors = mutableListOf<TurnRowError>()
    val lines = text.split('\n')
    var mapping = defaultMapping
    var headerConsumed = false

    lines.forEachIndexed { lineIndex, rawLine ->
        val rowNumber = lineIndex + 1
        val line = rawLine.trimEnd('\r')
        if (line.isBlank()) return@forEachIndexed
        val cells = splitCells(line)

        if (!headerConsumed) {
            val header = detectHeaderMapping(cells)
            headerConsumed = true
            if (header != null) {
                mapping = header
                return@forEachIndexed  // header line is not data
            }
        }

        fun cell(field: String): String? = mapping[field]?.let { cells.getOrNull(it) }

        val turnCell = cell("turn")
        val turn = turnCell?.let { parseIntCell(it) }
        if (turn == null) {
            errors.add(TurnRowError(rowNumber, "row $rowNumber: '${turnCell ?: ""}' is not a valid turn number"))
            return@forEachIndexed
        }
        records.add(
            ParsedTurnRow(
                turn = turn,
                fame = cell("fame")?.let { parseIntCell(it) } ?: 0,
                resourcePoints = cell("resourcePoints")?.let { parseIntCell(it) } ?: 0,
                consumption = cell("consumption")?.let { parseIntCell(it) } ?: 0,
                unrest = cell("unrest")?.let { parseIntCell(it) } ?: 0,
                xpAwarded = cell("xpAwarded")?.let { parseIntCell(it) },
                notes = cell("notes")?.takeIf { it.isNotBlank() },
            )
        )
    }
    return TurnRowsParseResult(records, errors)
}

/** Outcome of planning a merge of imported rows into existing turn history. */
data class TurnHistoryMergePlan(
    /** Turn numbers present in BOTH existing history and the import (imported replaces on confirm). */
    val collisions: List<Int>,
    /** Final turn numbers kept after merge + cap, turn-sorted. */
    val keptTurns: List<Int>,
    /** How many oldest records the cap trimmed from the merged set. */
    val trimmed: Int,
)

/**
 * Plan a turn-sorted union of [existingTurns] with [importedTurns]: imported turns replace same-turn
 * existing records (reported as [collisions]); the merged set keeps the newest [cap] turns (the
 * append cap exists for live growth, not correctness — imported backfill may exceed it, so the
 * dialog states how many were trimmed). Pure; the caller applies the actual record union.
 */
fun planTurnHistoryMerge(existingTurns: List<Int>, importedTurns: List<Int>, cap: Int): TurnHistoryMergePlan {
    val existing = existingTurns.toSet()
    val collisions = importedTurns.filter { it in existing }.distinct().sorted()
    val unionSorted = (existing + importedTurns).distinct().sorted()
    val kept = if (unionSorted.size > cap) unionSorted.takeLast(cap) else unionSorted
    return TurnHistoryMergePlan(
        collisions = collisions,
        keptTurns = kept,
        trimmed = (unionSorted.size - kept.size).coerceAtLeast(0),
    )
}
