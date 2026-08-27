package at.posselt.pfrpg2e.camping

import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface RumorRowContext {
    val id: String
    val text: String
    val sourceRegion: String?
    val stateLabel: String
    val isStale: Boolean
    val isExpired: Boolean
    val isPinned: Boolean
    val isConverted: Boolean
    /**
     * GM-only fields, NULL for players at the context level: veracity would turn hearsay into a
     * solved puzzle, and an expiry countdown ("3 days left") turns a rumor into a quest timer.
     * Players own the party actor, so a template conditional is layout, never the gate.
     */
    val veracityValue: String?
    val veracityLabel: String?
    val ageDays: Int?
    val canConvert: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface RumorBoardContext {
    val rows: Array<RumorRowContext>
    val hasAny: Boolean
    val isGM: Boolean
}

/** Literal keys in a when: t("...$state") is invisible to the i18n guard and ships raw. */
private fun rumorStateLabel(state: RumorState): String = when (state) {
    RumorState.FRESH -> t("camping.rumors.state.fresh")
    RumorState.STALE -> t("camping.rumors.state.stale")
    RumorState.EXPIRED -> t("camping.rumors.state.expired")
    RumorState.CONVERTED -> t("camping.rumors.state.converted")
    RumorState.PINNED -> t("camping.rumors.state.pinned")
}

private fun rumorVeracityLabel(veracity: RumorVeracity?): String = when (veracity) {
    RumorVeracity.TRUE -> t("camping.rumors.veracity.true")
    RumorVeracity.DISTORTED -> t("camping.rumors.veracity.distorted")
    RumorVeracity.FALSE -> t("camping.rumors.veracity.false")
    null -> t("camping.rumors.veracity.unassessed")
}

/**
 * Builds the rumor board. Rows without an id are still SHOWN -- the text is the value -- but
 * carry no GM controls, since every control addresses its rumor by id.
 */
fun buildRumorBoardContext(
    rumors: List<Rumor>,
    currentDay: Int,
    isGM: Boolean,
): RumorBoardContext {
    val rows = rumors.map { rumor ->
        RumorRowContext(
            id = rumor.id,
            text = rumor.text,
            sourceRegion = rumor.sourceRegion,
            stateLabel = rumorStateLabel(rumor.state),
            isStale = rumor.state == RumorState.STALE,
            isExpired = rumor.state == RumorState.EXPIRED,
            isPinned = rumor.state == RumorState.PINNED,
            isConverted = rumor.state == RumorState.CONVERTED,
            veracityValue = rumor.veracity?.value.takeIf { isGM },
            veracityLabel = rumorVeracityLabel(rumor.veracity).takeIf { isGM },
            ageDays = rumor.bornDay?.let { currentDay - it }?.coerceAtLeast(0).takeIf { isGM },
            canConvert = isGM && rumor.id.isNotBlank() &&
                    rumor.state != RumorState.CONVERTED,
        )
    }
    return RumorBoardContext(
        rows = rows.toTypedArray(),
        hasAny = rows.isNotEmpty(),
        isGM = isGM,
    )
}
