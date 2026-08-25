package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.camping.dialogs.downtimeKindLabel
import at.posselt.pfrpg2e.kingdom.data.RawPcDowntimeProject
import at.posselt.pfrpg2e.kingdom.downtime.DowntimeKind
import at.posselt.pfrpg2e.utils.t
import js.objects.recordOf
import kotlinx.js.JsPlainObject

/**
 * Camping-sheet Downtime Projects rows
 * (`docs/plans/2026-07-09-plan-downtime-projects.md` SS7, phase 4).
 *
 * Rows render for EVERY user -- a player checking their own craft is the point -- while the
 * controls (add, the chat offers) are GM surfaces. Name resolution is injected so the builder
 * stays testable without Foundry.
 */
@Suppress("unused")
@JsPlainObject
external interface DowntimeRowContext {
    val id: String
    val pcName: String
    val title: String
    val kindLabel: String
    val daysLabel: String
    val statusLabel: String
    val paused: Boolean
    val completed: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface DowntimeSectionContext {
    val rows: Array<DowntimeRowContext>
    val activeCount: Int
}

/** Literal keys in whens -- dynamic key assembly is invisible to the i18n guard. */
private fun downtimeStatusLabel(status: String?, pauseReason: String?): String = when (status) {
    "inProgress" -> t("camping.downtime.status.inProgress")
    "completed" -> t("camping.downtime.status.completed")
    "paused" ->
        if (pauseReason == "prerequisiteLost") {
            t("camping.downtime.status.pausedPrereq")
        } else {
            t("camping.downtime.status.paused")
        }
    else -> t("camping.downtime.status.unknown")
}

fun buildDowntimeSectionContext(
    projects: Array<RawPcDowntimeProject>?,
    resolveName: (String) -> String?,
): DowntimeSectionContext {
    val raws = projects ?: emptyArray()
    return DowntimeSectionContext(
        rows = raws.map { raw ->
            DowntimeRowContext(
                id = raw.id,
                pcName = resolveName(raw.pcActorUuid) ?: t("camping.downtime.unknownPc"),
                title = raw.title,
                kindLabel = downtimeKindLabel(DowntimeKind.fromValue(raw.kind)),
                daysLabel = t(
                    "camping.downtime.daysProgress",
                    recordOf(
                        "remaining" to raw.daysRemaining.toString(),
                        "total" to raw.daysTotal.toString(),
                    ),
                ),
                statusLabel = downtimeStatusLabel(raw.status, raw.pauseReason),
                paused = raw.status == "paused",
                completed = raw.status == "completed",
            )
        }.toTypedArray(),
        activeCount = raws.count { it.status == "inProgress" },
    )
}

@Suppress("unused")
@JsPlainObject
external interface DowntimeCompleteCardContext {
    val actorUuid: String
    val projectId: String
    val title: String
    val body: String
    /** Enriched @UUID link for craft targets; Foundry enriches it at render. Null otherwise. */
    val targetLink: String?
    val confirmLabel: String
    val extendLabel: String
}

fun downtimeCompleteContext(
    actorUuid: String,
    project: at.posselt.pfrpg2e.kingdom.downtime.DowntimeProject,
    targetRef: String?,
): DowntimeCompleteCardContext =
    DowntimeCompleteCardContext(
        actorUuid = actorUuid,
        projectId = project.id,
        title = t("camping.downtime.offer.title", recordOf("title" to project.title)),
        body = t(
            "camping.downtime.offer.body",
            recordOf(
                "kind" to downtimeKindLabel(project.kind),
                "days" to project.daysTotal.toString(),
            ),
        ),
        targetLink = targetRef
            ?.takeIf { project.kind == DowntimeKind.CRAFT && it.isNotBlank() }
            ?.let { "@UUID[$it]" },
        confirmLabel = t("camping.downtime.offer.confirm"),
        extendLabel = t("camping.downtime.offer.extend"),
    )

/** The roll each kind names at confirm time (plan SS5) -- literal keys in a when. */
fun downtimeRollPrompt(kind: DowntimeKind?): String = when (kind) {
    DowntimeKind.CRAFT -> t("camping.downtime.rollPrompt.craft")
    DowntimeKind.RETRAIN -> t("camping.downtime.rollPrompt.retrain")
    DowntimeKind.EARN_INCOME -> t("camping.downtime.rollPrompt.earnIncome")
    DowntimeKind.RITUAL -> t("camping.downtime.rollPrompt.ritual")
    null -> t("camping.downtime.rollPrompt.unknown")
}
