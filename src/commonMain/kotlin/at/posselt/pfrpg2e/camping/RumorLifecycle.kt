package at.posselt.pfrpg2e.camping

/**
 * The rumor state machine (plan section 5.2). Rumors age on the DAILY world clock -- the same
 * cadence as weather and companion travel, per the standing tick split -- and everything here is
 * pure and deterministic: days arrive as numbers, rolls arrive injected, and a preview therefore
 * always matches a commit.
 */

/** Days until a fresh rumor dims to stale. A guess at Kingmaker's travel cadence, deliberately a
 *  named constant so the table can retune it in one place (plan open question 2). */
const val RUMOR_STALE_AFTER_DAYS = 7

/** Days until a stale rumor expires and its beat is offered. */
const val RUMOR_EXPIRE_AFTER_DAYS = 21

/** How many rumors the store retains before the oldest DEAD ones are trimmed. */
const val RUMOR_CAP = 40

/** One rumor's transition during a tick, for the caller to persist and narrate. */
data class RumorTransition(
    val rumorId: String,
    val from: RumorState,
    val to: RumorState,
)

data class RumorTickOutcome(
    val rumors: List<Rumor>,
    val transitions: List<RumorTransition>,
)

/** Whether this state still ages. CONVERTED became a quest that owns its own lifecycle; PINNED is
 *  the GM saying "this one does not lapse"; EXPIRED has nowhere further to fall. */
private fun RumorState.ages(): Boolean = this == RumorState.FRESH || this == RumorState.STALE

/**
 * Advances every aging rumor across a [days]-day jump ending at [currentDay].
 *
 * A rumor with no [Rumor.bornDay] ADOPTS [currentDay] and ages from there: a row written before
 * the field existed must not be treated as infinitely old and expire on the first tick after the
 * upgrade. A multi-day jump (long rest chains, GM clock skips) can cross both thresholds at once;
 * the transition recorded is from the state the rumor actually held, so a fresh rumor that jumps
 * 30 days emits fresh -> expired, not a fabricated intermediate stop.
 *
 * A rumor with a blank id still ages -- aging is keyed on the object -- but emits NO transition,
 * because a transition exists to drive a chat beat and a beat must be able to name its rumor.
 */
fun tickRumors(rumors: List<Rumor>, currentDay: Int, days: Int): RumorTickOutcome {
    if (days <= 0) return RumorTickOutcome(rumors, emptyList())
    val transitions = mutableListOf<RumorTransition>()
    val next = rumors.map { rumor ->
        if (!rumor.state.ages()) return@map rumor
        val born = rumor.bornDay ?: return@map rumor.copy(bornDay = currentDay)
        val age = currentDay - born
        val target = when {
            age >= RUMOR_EXPIRE_AFTER_DAYS -> RumorState.EXPIRED
            age >= RUMOR_STALE_AFTER_DAYS -> RumorState.STALE
            else -> RumorState.FRESH
        }
        if (target == rumor.state) {
            rumor
        } else {
            if (rumor.id.isNotBlank()) {
                transitions.add(RumorTransition(rumorId = rumor.id, from = rumor.state, to = target))
            }
            rumor.copy(state = target)
        }
    }
    return RumorTickOutcome(rumors = next, transitions = transitions)
}

/**
 * Rumors whose expiry beat should be offered now: EXPIRED, never offered before, and addressable.
 * beatOfferedDay is checked for null rather than compared to [currentDay] -- a beat is offered
 * ONCE ever, and a declined one stays declined (the plan's "a declined beat never re-offers").
 */
fun beatCandidates(rumors: List<Rumor>, currentDay: Int): List<Rumor> =
    rumors.filter {
        it.state == RumorState.EXPIRED && it.beatOfferedDay == null && it.id.isNotBlank()
    }

/**
 * Trims the store to [cap], dropping the oldest DEAD rumors first -- expired, then converted.
 * A live lead (fresh/stale/pinned) is NEVER dropped, even over the cap: silently losing a rumor
 * the table might still chase is worse than a long list. Order is preserved for the survivors.
 */
fun capRumors(rumors: List<Rumor>, cap: Int = RUMOR_CAP): List<Rumor> {
    if (rumors.size <= cap) return rumors
    var excess = rumors.size - cap
    val dropIds = mutableSetOf<Int>()
    // two passes, expired before converted: a converted rumor still points at a quest the table
    // is playing, so it outlives an expired dead end
    for (state in listOf(RumorState.EXPIRED, RumorState.CONVERTED)) {
        if (excess <= 0) break
        rumors.forEachIndexed { index, rumor ->
            if (excess > 0 && rumor.state == state && index !in dropIds) {
                dropIds.add(index)
                excess--
            }
        }
    }
    return rumors.filterIndexed { index, _ -> index !in dropIds }
}

/**
 * Picks the expiry beat's prose template, deterministically, from an INJECTED roll -- the
 * SeededRng discipline, so a previewed beat matches the committed one. An empty table yields
 * null: a category with no authored prose degrades to a quiet expiry, never to a
 * wrong-flavoured beat.
 */
fun selectMutationBeat(
    rumor: Rumor,
    table: List<String>,
    roll: Int,
): String? {
    if (table.isEmpty()) return null
    val index = ((roll % table.size) + table.size) % table.size
    return table[index]
}
