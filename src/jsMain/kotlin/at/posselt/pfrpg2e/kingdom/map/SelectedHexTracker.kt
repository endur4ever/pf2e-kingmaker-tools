package at.posselt.pfrpg2e.kingdom.map

import com.foundryvtt.core.helpers.TypedHooks
import com.foundryvtt.kingmaker.onRenderKingmakerHexHud

/**
 * Remembers the last hex the GM opened on the Kingmaker map (its native hex key
 * as a string, matching `kingmaker.state.hexes` keys). Used to pre-fill the
 * "Add hex content" form so the GM can just click a hex on the map instead of
 * hunting for its id. Null until a hex is clicked this session.
 */
var lastSelectedHexKey: String? = null
    private set

fun registerSelectedHexTracker() {
    TypedHooks.onRenderKingmakerHexHud { hud, _, _ ->
        lastSelectedHexKey = hud.hex.key.toString()
    }
}
