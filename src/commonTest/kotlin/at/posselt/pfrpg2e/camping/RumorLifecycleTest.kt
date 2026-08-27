package at.posselt.pfrpg2e.camping

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RumorLifecycleTest {
    private fun rumor(
        id: String = "r1",
        born: Int? = 0,
        state: RumorState = RumorState.FRESH,
        offered: Int? = null,
    ) = Rumor(
        text = "Troll sightings near the ford",
        id = id,
        bornDay = born,
        state = state,
        beatOfferedDay = offered,
    )

    @Test
    fun aRumorDimsAtSevenDaysAndExpiresAtTwentyOne() {
        val day6 = tickRumors(listOf(rumor()), currentDay = 6, days = 1)
        assertEquals(RumorState.FRESH, day6.rumors.single().state)
        assertTrue(day6.transitions.isEmpty())

        val day7 = tickRumors(listOf(rumor()), currentDay = 7, days = 1)
        assertEquals(RumorState.STALE, day7.rumors.single().state)
        assertEquals(RumorState.FRESH, day7.transitions.single().from)
        assertEquals(RumorState.STALE, day7.transitions.single().to)

        val day21 = tickRumors(listOf(rumor(state = RumorState.STALE)), currentDay = 21, days = 1)
        assertEquals(RumorState.EXPIRED, day21.rumors.single().state)
    }

    @Test
    fun aLongJumpCrossesBothThresholdsInOneHonestTransition() {
        // a 30-day clock skip: fresh -> expired directly, recording the state it actually held
        val out = tickRumors(listOf(rumor()), currentDay = 30, days = 30)
        assertEquals(RumorState.EXPIRED, out.rumors.single().state)
        val t = out.transitions.single()
        assertEquals(RumorState.FRESH, t.from)
        assertEquals(RumorState.EXPIRED, t.to)
    }

    @Test
    fun pinnedConvertedAndExpiredRumorsDoNotAge() {
        val stuck = listOf(
            rumor(id = "p", state = RumorState.PINNED),
            rumor(id = "c", state = RumorState.CONVERTED),
            rumor(id = "e", state = RumorState.EXPIRED),
        )
        val out = tickRumors(stuck, currentDay = 100, days = 50)
        assertEquals(listOf(RumorState.PINNED, RumorState.CONVERTED, RumorState.EXPIRED),
            out.rumors.map { it.state })
        assertTrue(out.transitions.isEmpty(), "nothing to narrate: nothing moved")
    }

    @Test
    fun aRowWithoutABirthdayAdoptsTodayInsteadOfExpiringInstantly() {
        // a rumor persisted before bornDay existed must not be treated as infinitely old
        val out = tickRumors(listOf(rumor(born = null)), currentDay = 500, days = 1)
        val adopted = out.rumors.single()
        assertEquals(500, adopted.bornDay, "it ages from first sighting")
        assertEquals(RumorState.FRESH, adopted.state)
        assertTrue(out.transitions.isEmpty())
        // and from there the normal clock applies
        val later = tickRumors(out.rumors, currentDay = 507, days = 7)
        assertEquals(RumorState.STALE, later.rumors.single().state)
    }

    @Test
    fun aZeroDayTickIsANoOp() {
        val input = listOf(rumor(state = RumorState.STALE))
        val out = tickRumors(input, currentDay = 100, days = 0)
        assertEquals(input, out.rumors)
        assertTrue(out.transitions.isEmpty())
    }

    @Test
    fun anIdlessRumorAgesButNeverNarrates() {
        // a beat must be able to name its rumor; an unaddressable row changes state silently
        val out = tickRumors(listOf(rumor(id = "")), currentDay = 30, days = 1)
        assertEquals(RumorState.EXPIRED, out.rumors.single().state)
        assertTrue(out.transitions.isEmpty())
    }

    @Test
    fun beatCandidatesAreExpiredUnofferedAndAddressable() {
        val rumors = listOf(
            rumor(id = "due", state = RumorState.EXPIRED),
            rumor(id = "done", state = RumorState.EXPIRED, offered = 10),   // declined stays declined
            rumor(id = "live", state = RumorState.STALE),
            rumor(id = "", state = RumorState.EXPIRED),                      // unaddressable
        )
        assertEquals(listOf("due"), beatCandidates(rumors, currentDay = 25).map { it.id })
    }

    @Test
    fun theCapDropsDeadRumorsOldestFirstAndNeverALiveLead() {
        val rumors = buildList {
            repeat(3) { add(rumor(id = "expired-$it", state = RumorState.EXPIRED)) }
            repeat(2) { add(rumor(id = "converted-$it", state = RumorState.CONVERTED)) }
            repeat(3) { add(rumor(id = "live-$it")) }
        }
        val capped = capRumors(rumors, cap = 5)
        assertEquals(5, capped.size)
        // the three expired dropped first (oldest dead), then... only 3 needed: all expired gone
        assertTrue(capped.none { it.state == RumorState.EXPIRED })
        assertEquals(2, capped.count { it.state == RumorState.CONVERTED })
        assertEquals(3, capped.count { it.state == RumorState.FRESH })
    }

    @Test
    fun theCapDropsAnsweredExpiredBeforeAJustExpiredUnofferedOne() {
        // the tick a rumor expires INTO an over-cap store: its offer has not posted yet, and
        // trimming it first would silently skip the one consequence the feature promises
        val rumors = listOf(
            rumor(id = "answered", state = RumorState.EXPIRED, offered = 3),
            rumor(id = "just-expired", state = RumorState.EXPIRED),
            rumor(id = "live"),
        )
        val capped = capRumors(rumors, cap = 2)
        assertEquals(listOf("just-expired", "live"), capped.map { it.id },
            "the answered one had nothing left to say; the unoffered one still owes the GM a card")
    }

    @Test
    fun theCapPrefersDroppingExpiredOverConverted() {
        // a converted rumor still points at a quest the table is playing
        val rumors = listOf(
            rumor(id = "c", state = RumorState.CONVERTED),
            rumor(id = "e", state = RumorState.EXPIRED),
            rumor(id = "live"),
        )
        val capped = capRumors(rumors, cap = 2)
        assertEquals(listOf("c", "live"), capped.map { it.id })
    }

    @Test
    fun theCapNeverDropsLiveLeadsEvenWhenStillOverCap() {
        val rumors = List(6) { rumor(id = "live-$it") }
        assertEquals(6, capRumors(rumors, cap = 3).size,
            "silently losing a lead the table might chase is worse than a long list")
    }

    @Test
    fun mutationBeatsAreDeterministicAndDegradeToSilence() {
        val table = listOf("a", "b", "c")
        assertEquals("b", selectMutationBeat(rumor(), table, roll = 1))
        assertEquals("b", selectMutationBeat(rumor(), table, roll = 1), "same roll, same beat")
        assertEquals("c", selectMutationBeat(rumor(), table, roll = 5), "wraps by modulo")
        assertEquals("c", selectMutationBeat(rumor(), table, roll = -1), "negative rolls fold in")
        assertNull(selectMutationBeat(rumor(), emptyList(), roll = 3),
            "no authored prose degrades to a quiet expiry, never a wrong-flavoured beat")
    }
}
