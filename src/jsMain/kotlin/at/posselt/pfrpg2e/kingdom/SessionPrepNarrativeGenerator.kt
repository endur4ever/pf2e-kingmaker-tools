package at.posselt.pfrpg2e.kingdom

/**
 * Template-based narrative prose generator for the Session Prep dashboard (roadmap #10).
 *
 * Takes the structured [SessionPrepView] data and produces a GM-facing narrative recap
 * paragraph. This is a pure function — no LLM, no external dependencies, fully offline.
 * The prose is assembled from per-section templates that read the entry names and
 * deadlines from the view, producing a natural-language session prep summary.
 *
 * The output is an HTML string suitable for rendering in the sheet or exporting to
 * a Foundry Journal Entry.
 */
object SessionPrepNarrativeGenerator {

    // Quest/clock/hex/companion names and details are world data and may contain markup
    // characters; escape them so they can't inject into the dialog/journal HTML.
    private fun esc(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /**
     * Generates a narrative recap from the given [view].
     *
     * @param view the structured session prep data (open quests, clocks, events, hex hooks, companion moments)
     * @return an HTML string containing the narrative prose, or empty string if there's nothing to narrate
     */
    fun generate(view: SessionPrepView): String {
        if (!view.hasAnything) return ""

        val sb = StringBuilder()

        // Opening
        sb.append("<p><strong>Session Prep Recap</strong></p>\n")

        // Open Quests
        if (view.openQuests.isNotEmpty()) {
            sb.append("<h2>Open Quests</h2>\n")
            sb.append("<p>")
            val questNames = view.openQuests.joinToString(", ") { esc(it.name) }
            sb.append("The kingdom is currently dealing with the following open quests: $questNames.")
            sb.append("</p>\n")
        }

        // Active Clocks (GM only)
        if (view.isGM && view.activeClocks.isNotEmpty()) {
            sb.append("<h2>Active Campaign Clocks</h2>\n")
            sb.append("<p>")
            val clockDescriptions = view.activeClocks.joinToString("; ") { clock ->
                if (clock.turnsRemaining != null) {
                    "${esc(clock.name)} (${clock.turnsRemaining} turns remaining)"
                } else {
                    esc(clock.name)
                }
            }
            sb.append("Time-sensitive deadlines are ticking: $clockDescriptions.")
            sb.append("</p>\n")
        }

        // Unresolved Events (GM only)
        if (view.isGM && view.unresolvedEvents.isNotEmpty()) {
            sb.append("<h2>Unresolved Kingdom Events</h2>\n")
            sb.append("<p>")
            val eventNames = view.unresolvedEvents.joinToString(", ") { esc(it.name) }
            sb.append("The following kingdom events remain unresolved: $eventNames.")
            sb.append("</p>\n")
        }

        // Hex Hooks
        if (view.hexHooks.isNotEmpty()) {
            sb.append("<h2>Hex Content Hooks</h2>\n")
            sb.append("<p>")
            val hexDescriptions = view.hexHooks.joinToString("; ") { hex ->
                if (hex.detail.isNotBlank()) {
                    "${esc(hex.name)} at ${esc(hex.detail)}"
                } else {
                    esc(hex.name)
                }
            }
            sb.append("Notable locations to explore or revisit: $hexDescriptions.")
            sb.append("</p>\n")
        }

        // Companion Moments
        if (view.companionMoments.isNotEmpty()) {
            sb.append("<h2>Companion Moments</h2>\n")
            sb.append("<p>\n")
            val companionDescriptions = view.companionMoments.joinToString("; ") { moment ->
                buildString {
                    append(esc(moment.name))
                    if (moment.turnsRemaining != null) {
                        append(" (${moment.turnsRemaining} turns remaining)")
                    }
                    if (moment.detail.isNotBlank()) {
                        append(" [${esc(moment.detail)}]")
                    }
                }
            }
            sb.append("Personal companion storylines are active: $companionDescriptions.")
            sb.append("</p>\n")
        }

        // Companion Expeditions
        if (view.companionExpeditions.isNotEmpty()) {
            sb.append("<h2>Companion Expeditions</h2>\n")
            val inProgress = view.companionExpeditions.filter { it.status == "inProgress" }
            val awaiting = view.companionExpeditions.filter { it.status == "awaitingResolution" }

            if (inProgress.isNotEmpty()) {
                sb.append("<p>Companions currently away on expeditions: ")
                val desc = inProgress.joinToString("; ") { exp ->
                    val companionsText = if (exp.companionNames.isNotBlank()) "${esc(exp.companionNames)} on " else ""
                    val daysText = if (exp.turnsRemaining != null) " (${exp.turnsRemaining} days remaining)" else ""
                    "$companionsText${esc(exp.name)}$daysText"
                }
                sb.append("$desc.</p>\n")
            }

            if (awaiting.isNotEmpty()) {
                sb.append("<p>Expeditions recently completed and awaiting GM resolution:</p>\n<ul>\n")
                for (exp in awaiting) {
                    sb.append("  <li>")
                    val companionsText = if (exp.companionNames.isNotBlank()) "<strong>${esc(exp.companionNames)}</strong>: " else ""
                    val degreeLabel = exp.outcomeDegree?.let { d ->
                        when (d) {
                            "criticalSuccess" -> "Critical Success"
                            "success" -> "Success"
                            "failure" -> "Failure"
                            "criticalFailure" -> "Critical Failure"
                            else -> esc(d)
                        }
                    } ?: "Awaiting Roll"
                    sb.append("$companionsText${esc(exp.name)} — $degreeLabel")

                    val details = mutableListOf<String>()
                    if (exp.completesQuest) {
                        details.add("completed personal quest")
                    }
                    if (exp.willLevelUp) {
                        details.add("ready to level up")
                    }
                    if (exp.outcomeDegree == "criticalFailure") {
                        details.add("complication arose")
                    }
                    if (details.isNotEmpty()) {
                        sb.append(" (${details.joinToString(", ")})")
                    }
                    sb.append("</li>\n")
                }
                sb.append("</ul>\n")
            }
        }

        // Recent Turns (GM only)
        if (view.isGM && view.recentTurns.isNotEmpty()) {
            sb.append("<h2>Recent Turns</h2>\n")
            sb.append("<p>\n")
            val turnDescriptions = view.recentTurns.joinToString(". ") { t ->
                buildString {
                    append("Turn ${t.turn}: Fame ${t.fame}, ${t.resourcePoints} RP, ${t.consumption} consumption, ${t.unrest} unrest")
                    if (t.warPressure != null) append(", war pressure ${t.warPressure}")
                    if (t.xpAwarded != null) append(", ${t.xpAwarded} XP awarded")
                    if (!t.clockEvents.isNullOrEmpty()) append("; clock events: ${t.clockEvents.joinToString(", ") { esc(it) }}")
                }
            }
            sb.append("Recent kingdom history: $turnDescriptions.")
            sb.append("</p>\n")
        }

        return sb.toString()
    }

    /**
     * Generates a plain-text version of the narrative for use in chat messages
     * or other non-HTML contexts.
     */
    fun generatePlainText(view: SessionPrepView): String {
        if (!view.hasAnything) return ""

        val sb = StringBuilder()
        sb.appendLine("**Session Prep Recap**")

        if (view.openQuests.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("**Open Quests**")
            for (q in view.openQuests) {
                sb.appendLine("- ${q.name}${if (q.detail.isNotBlank()) " (${q.detail})" else ""}")
            }
        }

        if (view.isGM && view.activeClocks.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("**Active Campaign Clocks**")
            for (c in view.activeClocks) {
                val turns = if (c.turnsRemaining != null) " (${c.turnsRemaining} turns remaining)" else ""
                sb.appendLine("- ${c.name}$turns")
            }
        }

        if (view.isGM && view.unresolvedEvents.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("**Unresolved Kingdom Events**")
            for (e in view.unresolvedEvents) {
                sb.appendLine("- ${e.name}")
            }
        }

        if (view.hexHooks.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("**Hex Content Hooks**")
            for (h in view.hexHooks) {
                val loc = if (h.detail.isNotBlank()) " at ${h.detail}" else ""
                sb.appendLine("- ${h.name}$loc")
            }
        }

        if (view.companionMoments.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("**Companion Moments**")
            for (m in view.companionMoments) {
                val turns = if (m.turnsRemaining != null) " (${m.turnsRemaining} turns remaining)" else ""
                val detail = if (m.detail.isNotBlank()) " [${m.detail}]" else ""
                sb.appendLine("- ${m.name}$turns$detail")
            }
        }

        if (view.companionExpeditions.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("**Companion Expeditions**")
            val inProgress = view.companionExpeditions.filter { it.status == "inProgress" }
            val awaiting = view.companionExpeditions.filter { it.status == "awaitingResolution" }

            if (inProgress.isNotEmpty()) {
                sb.appendLine("Away on expeditions:")
                for (exp in inProgress) {
                    val companionsText = if (exp.companionNames.isNotBlank()) "${exp.companionNames} on " else ""
                    val daysText = if (exp.turnsRemaining != null) " (${exp.turnsRemaining} days remaining)" else ""
                    sb.appendLine("- $companionsText${exp.name}$daysText")
                }
            }
            if (awaiting.isNotEmpty()) {
                if (inProgress.isNotEmpty()) sb.appendLine()
                sb.appendLine("Completed expeditions awaiting resolution:")
                for (exp in awaiting) {
                    val companionsText = if (exp.companionNames.isNotBlank()) "${exp.companionNames}: " else ""
                    val degreeLabel = when (exp.outcomeDegree) {
                        "criticalSuccess" -> "Critical Success"
                        "success" -> "Success"
                        "failure" -> "Failure"
                        "criticalFailure" -> "Critical Failure"
                        else -> exp.outcomeDegree ?: "Awaiting Roll"
                    }
                    val details = mutableListOf<String>()
                    if (exp.completesQuest) details.add("completed personal quest")
                    if (exp.willLevelUp) details.add("ready to level up")
                    if (exp.outcomeDegree == "criticalFailure") details.add("complication arose")

                    val detailsText = if (details.isNotEmpty()) " (${details.joinToString(", ")})" else ""
                    sb.appendLine("- $companionsText${exp.name} — $degreeLabel$detailsText")
                }
            }
        }

        if (view.isGM && view.recentTurns.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("**Recent Turns**")
            for (t in view.recentTurns) {
                sb.appendLine("- Turn ${t.turn}: Fame ${t.fame}, ${t.resourcePoints} RP, ${t.consumption} consumption, ${t.unrest} unrest${if (t.warPressure != null) ", war pressure ${t.warPressure}" else ""}${if (t.xpAwarded != null) ", ${t.xpAwarded} XP" else ""}")
                if (!t.clockEvents.isNullOrEmpty()) {
                    sb.appendLine("  Clock events: ${t.clockEvents.joinToString(", ")}")
                }
                if (!t.notes.isNullOrBlank()) {
                    sb.appendLine("  Notes: ${t.notes}")
                }
            }
        }

        return sb.toString().trimEnd()
    }
}
