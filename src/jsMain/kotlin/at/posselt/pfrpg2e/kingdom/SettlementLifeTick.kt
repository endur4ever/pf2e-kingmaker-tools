package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.settlements.Settlement
import at.posselt.pfrpg2e.kingdom.settlementlife.CastMember
import at.posselt.pfrpg2e.kingdom.settlementlife.LifeEventHookKind
import at.posselt.pfrpg2e.kingdom.settlementlife.LifeEventTemplate
import at.posselt.pfrpg2e.kingdom.settlementlife.MAX_LIFE_EVENTS_PER_TURN
import at.posselt.pfrpg2e.kingdom.settlementlife.RosterMember
import at.posselt.pfrpg2e.kingdom.settlementlife.castFromRoster
import at.posselt.pfrpg2e.kingdom.settlementlife.eligibleTemplates
import at.posselt.pfrpg2e.kingdom.settlementlife.lifeEventChancePercent
import at.posselt.pfrpg2e.kingdom.settlementlife.weightedForSettlement
import at.posselt.pfrpg2e.kingdom.settlementlife.weightedPick
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.AnyObject
import com.foundryvtt.core.Game
import js.objects.recordOf
import kotlin.random.Random

/**
 * End Turn integration for settlement life events
 * (`docs/plans/2026-07-09-plan-settlement-life.md` phases 3 and 4).
 *
 * The pure engine owns eligibility, weighting, the cumulative walk and casting; this adapter
 * supplies what it deliberately does not — the two random draws, the calendar season, and the
 * world's settlements — and writes the record the offer handler will later find. The same shape
 * as the petition adapter, for the same reason: a green engine with no caller is not a feature.
 */

/** One event that fired this turn, ready for the digest and the gazette. */
data class LifeEventFired(
    val settlementId: String,
    val settlementName: String,
    val recordId: String,
    val templateId: String,
    val hookKind: LifeEventHookKind,
    val hookMagnitude: Int,
    /** Already localized and cast-interpolated. */
    val gazetteLine: String,
)

/** Rotate settlements by turn so the same town cannot take both capped slots every month. */
private fun <T> rotateByTurn(items: List<T>, turn: Int): List<T> {
    if (items.size < 2) return items
    val offset = ((turn % items.size) + items.size) % items.size
    return items.drop(offset) + items.take(offset)
}

/**
 * Fill a template's cast slots from the roster, honouring `distinctFrom` by removing residents
 * already cast into the named slots. Each slot tries its preferred occupations in order and
 * falls back to the first remaining resident; an empty roster casts an ephemeral "someone" so a
 * town whose GM never filled the roster still gets a readable line.
 */
private fun castTemplate(
    template: LifeEventTemplate,
    roster: List<RosterMember>,
): Map<String, CastMember> {
    val cast = linkedMapOf<String, CastMember>()
    for ((slot, preferred, distinctFrom) in template.castSlots) {
        val taken = distinctFrom.mapNotNull { cast[it]?.npcId }.toSet()
        val available = roster.filter { it.id !in taken }
        val member = preferred.asSequence()
            .mapNotNull { occupation ->
                // castFromRoster falls back to first-resident on a miss, so a real occupation
                // match must be checked for explicitly to keep trying the next preference
                available.firstOrNull { it.occupation.trim().equals(occupation.trim(), ignoreCase = true) }
                    ?.let { CastMember(it.id, it.name) }
            }
            .firstOrNull()
            ?: castFromRoster(available, preferredOccupation = null, fallbackName = t("settlementLife.cast.someone"))
        if (member != null) cast[slot] = member
    }
    return cast
}

/**
 * Roll this turn's life events, mutating each acting settlement's `lifeEventHistory` in place.
 *
 * Returns only what fired here. The rolls are parameters so the cadence, the cap, the cooldown
 * and the season gate are testable without a Foundry world. At most one roll per settlement per
 * turn, at most [MAX_LIFE_EVENTS_PER_TURN] kingdom-wide, settlements visited in a turn-rotated
 * order so a busy capital cannot monopolise both slots forever.
 */
fun rollSettlementLifeEvents(
    kingdom: KingdomData,
    settlements: List<Settlement>,
    season: String?,
    currentTurn: Int,
    chanceRoll: () -> Int = { Random.nextInt(1, 101) },
    pickRoll: (Int) -> Int = { bound -> Random.nextInt(0, bound) },
): List<LifeEventFired> {
    val catalog = settlementLifeTemplates()
    if (catalog.isEmpty()) return emptyList()
    val gazetteKeys = rawSettlementLifeTemplates().associate { it.id to it.gazette }
    val fired = mutableListOf<LifeEventFired>()

    for (settlement in rotateByTurn(settlements, currentTurn)) {
        if (fired.size >= MAX_LIFE_EVENTS_PER_TURN) break
        val raw = kingdom.settlements.find { it.sceneId == settlement.id } ?: continue
        val population = settlement.size.populationNumber
        if (chanceRoll() > lifeEventChancePercent(settlement.level, population)) continue

        val history = raw.lifeEventHistory ?: emptyArray()
        // ONE roll per settlement per turn. Re-entering End Turn on the same turn number -- the
        // undo-and-redo path -- must not roll again: the cooldown alone would only exclude the
        // template that already fired and happily pick a different one on top of it.
        if (history.any { it.turn == currentTurn }) continue
        val lastFired = history.groupBy { it.templateId }.mapValues { (_, rs) -> rs.maxOf { it.turn } }
        // BASE ids (plan 6.1): the catalog names "tavern-dive", the V&K variant is "tavern-dive-vk",
        // and the engine's match is exact -- raw ids would make every template that names a tavern
        // silently un-fireable in a town whose only tavern is the V&K one
        val structureIds = settlement.baseStructureIds
        val eligible = eligibleTemplates(
            templates = catalog,
            structureIds = structureIds,
            season = season,
            population = population,
            settlementLevel = settlement.level,
            lastFiredByTemplate = lastFired,
            currentTurn = currentTurn,
        )
        if (eligible.isEmpty()) continue
        val weighted = weightedForSettlement(eligible, structureIds, season)
        val total = weighted.sumOf { maxOf(it.weight, 0) }
        if (total <= 0) continue
        val template = weightedPick(weighted, pickRoll(total)) ?: continue

        val roster = settlement.populationRoster.npcs.map { RosterMember(it.id, it.name, it.occupation) }
        val cast = castTemplate(template, roster)
        val gazetteKey = gazetteKeys[template.id] ?: continue
        val params = recordOf<String, Any?>("settlement" to settlement.name)
        cast.forEach { (slot, member) -> params[slot] = member.name }
        val recordId = "life-${settlement.id}-$currentTurn-${template.id}"

        raw.lifeEventHistory = history + RawSettlementLifeEventRecord(
            recordId = recordId,
            templateId = template.id,
            turn = currentTurn,
            castNpcIds = cast.values.mapNotNull { it.npcId }.toTypedArray(),
            castNames = cast.values.map { it.name }.toTypedArray(),
            hookKind = template.hookKind.value,
            hookMagnitude = template.hookMagnitude,
            hookApplied = template.hookKind == LifeEventHookKind.NONE,
        )
        fired += LifeEventFired(
            settlementId = settlement.id,
            settlementName = settlement.name,
            recordId = recordId,
            templateId = template.id,
            hookKind = template.hookKind,
            hookMagnitude = template.hookMagnitude,
            gazetteLine = t(gazetteKey, params.unsafeCast<AnyObject>()),
        )
    }
    return fired
}

/** The button label for a hook; null for flavor-only events, which get no button (§5.2). */
fun lifeEventHookLabel(kind: LifeEventHookKind, magnitude: Int): String? = when (kind) {
    LifeEventHookKind.NONE -> null
    LifeEventHookKind.UNREST -> if (magnitude < 0) t("settlementLife.hook.unrestDown") else t("settlementLife.hook.unrestUp")
    LifeEventHookKind.RP -> t("settlementLife.hook.rp")
    LifeEventHookKind.QUEST -> t("settlementLife.hook.quest")
    LifeEventHookKind.RUMOR -> t("settlementLife.hook.rumor")
}

/**
 * ONE whispered digest per turn (§4.1), grouped by row with each row's own apply/dismiss.
 *
 * Every mechanical change on it is a GM-confirmed offer; the gazette line has already been
 * written whether or not anyone clicks. Posted AFTER the single persist, like every offer.
 */
suspend fun postSettlementLifeDigest(
    game: Game,
    actorUuid: String,
    turn: Int,
    fired: List<LifeEventFired>,
) {
    if (fired.isEmpty()) return
    val gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray()
    // an EMPTY whisper array posts publicly, not to nobody
    if (gmUserIds.isEmpty()) return
    postChatTemplate(
        templatePath = "chatmessages/settlement-life-digest.hbs",
        templateContext = recordOf<String, Any?>(
            "actorUuid" to actorUuid,
            "turn" to turn,
            "rows" to fired.map { event ->
                recordOf<String, Any?>(
                    "settlementId" to event.settlementId,
                    "settlementName" to event.settlementName,
                    "recordId" to event.recordId,
                    "gazetteLine" to event.gazetteLine,
                    "hookKind" to event.hookKind.value,
                    "hookLabel" to lifeEventHookLabel(event.hookKind, event.hookMagnitude),
                )
            }.toTypedArray(),
        ),
        whisper = gmUserIds,
    )
}
