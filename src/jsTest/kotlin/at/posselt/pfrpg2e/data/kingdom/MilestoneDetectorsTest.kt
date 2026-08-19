package at.posselt.pfrpg2e.data.kingdom

import at.posselt.pfrpg2e.data.kingdom.ROAD_FEATURE_TYPE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import js.objects.ReadonlyRecord
import com.foundryvtt.kingmaker.HexState
import com.foundryvtt.kingmaker.HexFeature

/** Pure-logic tests for milestone detection functions. */
class MilestoneDetectorsTest {

    // Using external HexState and HexFeature interfaces via casting in js() blocks.

    // ── roadConnectedToCapital tests ────────────────────────────────────────

    @Test
    fun `roadConnectedToCapital returns true when settlement is capital`() {
        val roadHexes = setOf("hex-1")
        val neighbors = { _: String -> emptySet<String>() }

        val result = roadConnectedToCapital("hex-1", "hex-1", roadHexes, neighbors)

        assertTrue(result, "Settlement and capital are the same hex")
    }

    @Test
    fun `roadConnectedToCapital returns false when settlement has no road`() {
        val roadHexes = setOf("hex-capital")
        val neighbors = { _: String -> emptySet<String>() }

        val result = roadConnectedToCapital("hex-settlement", "hex-capital", roadHexes, neighbors)

        assertFalse(result, "Settlement hex must have a road feature")
    }

    @Test
    fun `roadConnectedToCapital returns false when capital has no road`() {
        val roadHexes = setOf("hex-settlement")
        val neighbors = { _: String -> emptySet<String>() }

        val result = roadConnectedToCapital("hex-settlement", "hex-capital", roadHexes, neighbors)

        assertFalse(result, "Capital hex must have a road feature")
    }

    @Test
    fun `a settlement merely ADJACENT to the road network is not connected`() {
        // The existing "no road" tests hand the BFS an empty neighbour function, so it fails for
        // want of adjacency and never reaches the both-endpoints rule at all -- deleting that rule
        // leaves them green. This is the case that actually pins it: there IS a complete road chain
        // running past the settlement to the capital, but the settlement's own hex has no road on
        // it. Standing next to a road is not being on it, so the milestone must not fire.
        val roadHexes = setOf("hex-mid", "hex-capital")
        val neighbors = { key: String ->
            when (key) {
                "hex-settlement" -> setOf("hex-mid")
                "hex-mid" -> setOf("hex-settlement", "hex-capital")
                "hex-capital" -> setOf("hex-mid")
                else -> emptySet()
            }
        }

        val result = roadConnectedToCapital("hex-settlement", "hex-capital", roadHexes, neighbors)

        assertFalse(result, "The settlement's own hex must carry a road, not merely touch one")
    }

    @Test
    fun `a capital merely ADJACENT to the road network is not connected`() {
        // Same rule from the other end: the road stops one hex short of the capital.
        val roadHexes = setOf("hex-settlement", "hex-mid")
        val neighbors = { key: String ->
            when (key) {
                "hex-settlement" -> setOf("hex-mid")
                "hex-mid" -> setOf("hex-settlement", "hex-capital")
                "hex-capital" -> setOf("hex-mid")
                else -> emptySet()
            }
        }

        val result = roadConnectedToCapital("hex-settlement", "hex-capital", roadHexes, neighbors)

        assertFalse(result, "The capital's own hex must carry a road, not merely touch one")
    }

    @Test
    fun `an identical settlement and capital hex needs no road at all`() {
        // The same-hex short circuit sits ABOVE the both-endpoints rule, so a capital that is its
        // own settlement trivially qualifies even with no roads built anywhere. Pinning this stops
        // a future reorder of those two checks from silently changing the answer.
        val result = roadConnectedToCapital("hex-1", "hex-1", emptySet(), { emptySet() })

        assertTrue(result, "A settlement that IS the capital is connected by definition")
    }

    @Test
    fun `roadConnectedToCapital returns true for direct road connection`() {
        val roadHexes = setOf("hex-settlement", "hex-capital")
        val neighbors = { key: String ->
            when (key) {
                "hex-settlement" -> setOf("hex-capital")
                "hex-capital" -> setOf("hex-settlement")
                else -> emptySet()
            }
        }

        val result = roadConnectedToCapital("hex-settlement", "hex-capital", roadHexes, neighbors)

        assertTrue(result, "Direct neighbor with roads on both ends should connect")
    }

    @Test
    fun `roadConnectedToCapital returns true for multi-hop road chain`() {
        val roadHexes = setOf("hex-settlement", "hex-mid", "hex-capital")
        val neighbors = { key: String ->
            when (key) {
                "hex-settlement" -> setOf("hex-mid")
                "hex-mid" -> setOf("hex-settlement", "hex-capital")
                "hex-capital" -> setOf("hex-mid")
                else -> emptySet()
            }
        }

        val result = roadConnectedToCapital("hex-settlement", "hex-capital", roadHexes, neighbors)

        assertTrue(result, "Road chain through intermediate hex should connect")
    }

    @Test
    fun `roadConnectedToCapital returns false when road chain is broken`() {
        val roadHexes = setOf("hex-settlement", "hex-mid", "hex-capital")
        val neighbors = { key: String ->
            when (key) {
                "hex-settlement" -> setOf("hex-mid")
                "hex-mid" -> setOf("hex-settlement") // Missing connection to capital!
                "hex-capital" -> setOf("hex-mid")
                else -> emptySet()
            }
        }

        val result = roadConnectedToCapital("hex-settlement", "hex-capital", roadHexes, neighbors)

        assertFalse(result, "Broken road chain should not connect")
    }

    @Test
    fun `roadConnectedToCapital returns false when intermediate hex lacks road feature`() {
        // hex-mid is a neighbor but NOT in roadHexes (no road feature)
        val roadHexes = setOf("hex-settlement", "hex-capital")
        val neighbors = { key: String ->
            when (key) {
                "hex-settlement" -> setOf("hex-mid")
                "hex-mid" -> setOf("hex-settlement", "hex-capital")
                "hex-capital" -> setOf("hex-mid")
                else -> emptySet()
            }
        }

        val result = roadConnectedToCapital("hex-settlement", "hex-capital", roadHexes, neighbors)

        assertFalse(result, "Intermediate hex without road feature breaks connection")
    }

    @Test
    fun `getRoadHexKeys returns hex keys that have road feature`() {
        val hexStates: ReadonlyRecord<String, HexState> = js("""{
            "hex-1": { features: [{ type: "road" }], claimed: true },
            "hex-2": { features: [{ type: "farmland" }], claimed: true },
            "hex-3": { features: [{ type: "road" }, { type: "bridge" }], claimed: false },
            "hex-4": { features: [], claimed: true }
        }""").unsafeCast<ReadonlyRecord<String, HexState>>()

        val result = getRoadHexKeys(hexStates)

        assertEquals(setOf("hex-1", "hex-3"), result)
    }

    @Test
    fun `getRoadHexKeys returns empty set when no roads exist`() {
        val hexStates: ReadonlyRecord<String, HexState> = js("""{
            "hex-1": { features: [{ type: "farmland" }], claimed: true },
            "hex-2": { features: [], claimed: true }
        }""").unsafeCast<ReadonlyRecord<String, HexState>>()

        val result = getRoadHexKeys(hexStates)

        assertTrue(result.isEmpty())
    }

    // ── regionFullyClaimed tests ────────────────────────────────────────────

    @Test
    fun `regionFullyClaimed returns false for empty region`() {
        val regionHexKeys = emptySet<String>()
        val hexStates: ReadonlyRecord<String, HexState> = js("{}").unsafeCast<ReadonlyRecord<String, HexState>>()

        val result = regionFullyClaimed(regionHexKeys, hexStates)

        assertFalse(result, "Empty region should not be considered fully claimed")
    }

    @Test
    fun `regionFullyClaimed returns true when all hexes claimed`() {
        val regionHexKeys = setOf("hex-1", "hex-2", "hex-3")
        val hexStates: ReadonlyRecord<String, HexState> = js("""{
            "hex-1": { claimed: true },
            "hex-2": { claimed: true },
            "hex-3": { claimed: true }
        }""").unsafeCast<ReadonlyRecord<String, HexState>>()

        val result = regionFullyClaimed(regionHexKeys, hexStates)

        assertTrue(result, "All hexes claimed should return true")
    }

    @Test
    fun `regionFullyClaimed returns false when one hex unclaimed`() {
        val regionHexKeys = setOf("hex-1", "hex-2", "hex-3")
        val hexStates: ReadonlyRecord<String, HexState> = js("""{
            "hex-1": { claimed: true },
            "hex-2": { claimed: false },
            "hex-3": { claimed: true }
        }""").unsafeCast<ReadonlyRecord<String, HexState>>()

        val result = regionFullyClaimed(regionHexKeys, hexStates)

        assertFalse(result, "One unclaimed hex should return false")
    }

    @Test
    fun `regionFullyClaimed returns false when hex missing from state`() {
        val regionHexKeys = setOf("hex-1", "hex-2", "hex-3")
        val hexStates: ReadonlyRecord<String, HexState> = js("""{
            "hex-1": { claimed: true },
            "hex-3": { claimed: true }
        }""").unsafeCast<ReadonlyRecord<String, HexState>>()

        val result = regionFullyClaimed(regionHexKeys, hexStates)

        assertFalse(result, "Missing hex state should return false")
    }

    @Test
    fun `regionFullyClaimed returns false when claimed is null`() {
        val regionHexKeys = setOf("hex-1", "hex-2")
        val hexStates: ReadonlyRecord<String, HexState> = js("""{
            "hex-1": { claimed: true },
            "hex-2": { }
        }""").unsafeCast<ReadonlyRecord<String, HexState>>()

        val result = regionFullyClaimed(regionHexKeys, hexStates)

        assertFalse(result, "Null claimed should be treated as unclaimed")
    }
}