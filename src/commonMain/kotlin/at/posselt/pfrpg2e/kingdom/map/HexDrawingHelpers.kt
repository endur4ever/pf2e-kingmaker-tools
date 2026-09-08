package at.posselt.pfrpg2e.kingdom.map

// ── Explored drawing helpers (pure logic, testable without Foundry runtime) ──

const val EXPLORED_DRAWING_TYPE = "explored"

// Visual constants for the explored hex drawing — distinct from claimed (solid fill).
// Explored uses a dashed outline + fog-of-war tint so GMs can tell them apart at a glance.
const val EXPLORED_FILL_COLOR = "#1a3a5c"
const val EXPLORED_FILL_ALPHA = 0.12
const val EXPLORED_STROKE_COLOR = "#4a90d9"
const val EXPLORED_STROKE_WIDTH = 3
const val EXPLORED_STROKE_DASH = "8,4"   // dash length, gap length — Foundry drawing config format

fun shouldHaveExploredDrawing(explored: Boolean?): Boolean = explored == true

// ── Cleared drawing helpers ──

const val CLEARED_DRAWING_TYPE = "cleared"

// Visual constants for the cleared hex drawing.
// Uses a warm orange fill with a dotted stroke — distinct from claimed (solid green)
// and explored (dashed blue). Orange evokes "worked/cleared" terrain.
const val CLEARED_FILL_COLOR = "#ff8c00"
const val CLEARED_FILL_ALPHA = 0.15
const val CLEARED_STROKE_COLOR = "#ff6600"
const val CLEARED_STROKE_WIDTH = 2
const val CLEARED_STROKE_DASH = "2,4"   // dotted pattern (short dash, wide gap)

fun shouldHaveClearedDrawing(cleared: Boolean?): Boolean = cleared == true

// ── Road drawing helpers ──

const val ROAD_DRAWING_TYPE = "road"

// The native Kingmaker module stores roads as a hex *feature* (HexFeature.type), not as a
// boolean — same as "farmland". Feature type values are the lowercase form of the native
// KINGMAKER.FEATURES keys.
const val ROAD_FEATURE_TYPE = "road"

// Visual constants for road drawings.
// Roads render as solid brown lines connecting hex centers.
const val ROAD_STROKE_COLOR = "#8B4513"
const val ROAD_STROKE_WIDTH = 4

/**
 * Whether a hex should render a road, based on its native feature types. A hex counts as a road
 * hex when any of its features is of type [ROAD_FEATURE_TYPE]. [featureTypes] is the list of
 * `HexFeature.type` values for the hex (null/absent types are ignored).
 */
fun shouldHaveRoadDrawing(featureTypes: List<String?>?): Boolean =
    featureTypes?.any { it == ROAD_FEATURE_TYPE } == true

// ── Ownership of realm-tile-flagged drawings ──

const val CLAIMED_DRAWING_TYPE = "claimed"

/** Overlay types this module's hex sync draws, one per native Kingmaker hex. */
val MANAGED_OVERLAY_TYPES = setOf(CLAIMED_DRAWING_TYPE, EXPLORED_DRAWING_TYPE, CLEARED_DRAWING_TYPE)

/**
 * Overlay types only ever written by this module. [CLAIMED_DRAWING_TYPE] is deliberately absent:
 * the Edit Realm Tile macro lets a GM tag a drawing of their own as claimed, and the realm parser
 * reads exactly those hand-tagged drawings to build a Tile-Based realm.
 */
val MODULE_ONLY_OVERLAY_TYPES = setOf(EXPLORED_DRAWING_TYPE, CLEARED_DRAWING_TYPE)

/**
 * Whether a realm-tile-flagged drawing was drawn by this module's hex sync, and may therefore be
 * deleted or made non-interactive when overlays are torn down.
 *
 * Managed overlays stamp the native hex key they belong to. A drawing a GM tagged by hand through
 * the Edit Realm Tile macro never carries one, so a flagged drawing with no [hexKey] is user data
 * unless its [type] is one only this module writes.
 */
fun isManagedHexOverlay(type: String?, hexKey: String?): Boolean =
    type != null && ((hexKey != null && type in MANAGED_OVERLAY_TYPES) || type in MODULE_ONLY_OVERLAY_TYPES)

/**
 * Whether a managed overlay is left over from an older build and must be deleted so the per-hex
 * sync can recreate it: overlays drawn as rectangles before hexes were drawn as polygons, and
 * overlays from before the hex key was stamped. Only applies to drawings [isManagedHexOverlay]
 * recognises as ours, so a hand-tagged claimed drawing is never swept up by the migration.
 */
fun isStaleHexOverlay(type: String?, hexKey: String?, shapeType: String?): Boolean =
    isManagedHexOverlay(type, hexKey) && (shapeType != "p" || hexKey == null)
