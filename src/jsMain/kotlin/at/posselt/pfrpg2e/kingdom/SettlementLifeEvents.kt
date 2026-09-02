package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.settlementlife.LifeEventHookKind
import at.posselt.pfrpg2e.kingdom.settlementlife.LifeEventTemplate
import at.posselt.pfrpg2e.kingdom.settlementlife.clampLifeEventUnrest
import kotlinx.js.JsPlainObject

/**
 * The settlement life-event catalog (`docs/plans/2026-07-09-plan-settlement-life.md` 2.1/2.2).
 *
 * One JSON file per template under `data/settlement-life-events/`, bundled by the existing
 * combineJsonFiles task and validated at build time by validateSettlementLifeEvents.
 */
@JsPlainObject
external interface RawStructureWeight {
    var anyOf: Array<String>
    var multiplier: Double
}

@JsPlainObject
external interface RawSeasonWeights {
    var spring: Double?
    var summer: Double?
    var fall: Double?
    var winter: Double?
}

@JsPlainObject
external interface RawCastSlot {
    var slot: String
    var preferOccupation: Array<String>?
    var distinctFrom: Array<String>?
}

@JsPlainObject
external interface RawLifeEventHook {
    /** A LifeEventHookKind.value from the closed set; anything else drops the hook, not the event. */
    var kind: String
    var magnitude: Int?
    var questTemplate: String?
}

@JsPlainObject
external interface RawSettlementLifeEvent {
    var id: String
    var name: String
    var gazette: String
    var category: String
    var baseWeight: Int
    var minSettlementLevel: Int?
    var requiresStructures: Array<String>?
    var structureWeights: Array<RawStructureWeight>?
    var seasonWeights: RawSeasonWeights?
    var cast: Array<RawCastSlot>?
    var hook: RawLifeEventHook?
    var cooldownTurns: Int?
}

/** One fired event, kept on the settlement so the offer and the NPC-memory seam can find it. */
@JsPlainObject
external interface RawSettlementLifeEventRecord {
    /** "life-<sceneId>-<turn>-<templateId>". */
    var recordId: String
    var templateId: String
    var turn: Int
    /** Roster RawNpcEntry ids cast into slots. */
    var castNpcIds: Array<String>
    var castNames: Array<String>
    var hookKind: String
    var hookMagnitude: Int?
    /** The GM answered the offer either way; the idempotency guard, like RawWarThreat.offerConsumed. */
    var hookApplied: Boolean?
}

@JsModule("./settlement-life-events.json")
private external val rawSettlementLifeEvents: Array<RawSettlementLifeEvent>

/**
 * The catalog as the pure engine sees it. A template whose hook names a kind outside the closed
 * set keeps its flavor and loses its hook -- one bad file must not remove the whole event, and it
 * certainly must not crash the tick.
 */
fun settlementLifeTemplates(): List<LifeEventTemplate> = rawSettlementLifeEvents.map { it.toLifeTemplate() }

/** The catalog mapping, exposed so the parse-time constraints can be tested against a synthetic row. */
fun RawSettlementLifeEvent.toLifeTemplate(): LifeEventTemplate = let { raw ->
        val hookKind = LifeEventHookKind.fromValue(raw.hook?.kind) ?: LifeEventHookKind.NONE
        LifeEventTemplate(
            id = raw.id,
            weight = raw.baseWeight,
            minSettlementLevel = raw.minSettlementLevel ?: 0,
            requiresStructures = raw.requiresStructures?.toList() ?: emptyList(),
            structureWeights = (raw.structureWeights ?: emptyArray()).map {
                it.anyOf.toList() to it.multiplier
            },
            seasonWeights = buildMap {
                raw.seasonWeights?.spring?.let { put("spring", it) }
                raw.seasonWeights?.summer?.let { put("summer", it) }
                raw.seasonWeights?.fall?.let { put("fall", it) }
                raw.seasonWeights?.winter?.let { put("winter", it) }
            },
            castSlots = (raw.cast ?: emptyArray()).map { slot ->
                Triple(
                    slot.slot,
                    slot.preferOccupation?.toList() ?: emptyList(),
                    slot.distinctFrom?.toList() ?: emptyList(),
                )
            },
            hookKind = hookKind,
            // section 5.2's closed constraints, enforced HERE because this is the only place a
            // magnitude enters the system: unrest moves one point either way, RP by 0 or 1. The
            // button labels are fixed strings ("+1 Unrest"), so an unclamped 10 would be a card
            // promising one thing and a click doing another
            hookMagnitude = when (hookKind) {
                LifeEventHookKind.UNREST -> clampLifeEventUnrest(raw.hook?.magnitude ?: 1).let { if (it == 0) 1 else it }
                LifeEventHookKind.RP -> (raw.hook?.magnitude ?: 1).coerceIn(0, 1)
                else -> 0
            },
            cooldownTurns = raw.cooldownTurns ?: 0,
        )
    }

/** Raw catalog access for guards that must read the DATA rather than the mapped view. */
fun rawSettlementLifeTemplates(): Array<RawSettlementLifeEvent> = rawSettlementLifeEvents
