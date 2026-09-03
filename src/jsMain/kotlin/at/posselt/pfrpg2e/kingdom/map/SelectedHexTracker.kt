package at.posselt.pfrpg2e.kingdom.map

import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.kingmaker.onRenderHexHud

/**
 * Remembers the last hex the GM opened on the Kingmaker map (its native hex key
 * as a string, matching `kingmaker.state.hexes` keys). Used to pre-fill the
 * "Add hex content" form so the GM can just click a hex on the map instead of
 * hunting for its id. Null until a hex is clicked this session.
 */
var lastSelectedHexKey: String? = null
    private set

fun registerSelectedHexTracker() {
    // renderHexHUD is what pf2e-kingmaker 2.3.x fires (class HexHUD); the old
    // renderKingmakerHexHUD binding never fired, so this tracker -- and the "Add hex content"
    // prefill it feeds -- was dead. The hovered hex rides on app.hex; its key is a Number.
    TypedHooks.onRenderHexHud { app, _, _ ->
        val rawKey = app.asDynamic().hex?.key
        val key = (rawKey as? Int)?.toString() ?: (rawKey as? Double)?.toInt()?.toString()
        if (key != null) lastSelectedHexKey = key
    }
}
