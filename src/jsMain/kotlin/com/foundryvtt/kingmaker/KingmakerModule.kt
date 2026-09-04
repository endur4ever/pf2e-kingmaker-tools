package com.foundryvtt.kingmaker

import com.foundryvtt.core.packages.Module
import com.foundryvtt.core.utils.Collection
import js.objects.ReadonlyRecord
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface HexFeature {
    val type: String?
}

/**
 * One entry of `kingmaker.state.hexes`, typed from the served pf2e-kingmaker 2.3.x
 * `KingmakerHexData` schema (verified 2026-09-03): exploration is a NUMBER (0 none, 1 recon,
 * 2 map), and there is no `explored` boolean at all. Read [isExplored], never `explored`.
 */
@JsPlainObject
external interface HexState {
    val commodity: String?
    val camp: String?
    val features: Array<HexFeature>?
    val claimed: Boolean?
    /** STALE: not in the 2.3.x schema; always undefined on a live world. Kept for old data only. */
    @Deprecated("pf2e-kingmaker 2.3.x stores exploration as a number; use isExplored()", ReplaceWith("isExplored()"))
    val explored: Boolean?
    /** kingmaker.CONST.EXPLORATION_STATES value: 0 NONE, 1 RECON, 2 MAP. */
    val exploration: Int?
    val discovered: Boolean?
    val cleared: Boolean?
}

/** Reconnoitered or mapped. Tolerates the pre-2.3 boolean so old flags still read. */
@Suppress("DEPRECATION")
fun HexState.isExplored(): Boolean = (exploration ?: 0) > 0 || explored == true


@JsPlainObject
external interface KingmakerState {
    val hexes: ReadonlyRecord<String, HexState>
}

@JsPlainObject
external interface KingmakerRegion {
    val hexes: Collection<KingmakerHex>
}

external class KingmakerModule : Module {
    val state: KingmakerState
    val region: KingmakerRegion
}

external val kingmaker: KingmakerModule