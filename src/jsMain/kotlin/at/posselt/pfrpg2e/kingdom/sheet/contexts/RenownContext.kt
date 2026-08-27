package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.data.kingdom.MAX_POPULACE_RENOWN
import at.posselt.pfrpg2e.kingdom.data.RawPcRenown
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface EpithetChipContext {
    val id: String
    val label: String
}

@Suppress("unused")
@JsPlainObject
external interface FactionRenownChipContext {
    val factionName: String
    val renown: Int
    val label: String
}

@Suppress("unused")
@JsPlainObject
external interface PcRenownContext {
    val actorUuid: String
    val name: String
    val roleLabel: String?
    /**
     * Withheld fields, NULLABLE by design. A viewer who is neither the GM nor this PC's owner gets
     * null rather than 0 -- players own the party actor, so anything that reaches the context is
     * readable whatever the template renders, and a non-nullable Int would force the builder to
     * ship either the real number or a lie.
     */
    val populace: Int?
    val populacePct: Int?
    val factionRenown: Array<FactionRenownChipContext>?
    val purchaseAccessTier: Int?
    val hasNumbers: Boolean
    /** Always present: an epithet is a public honour, announced in chat when it is granted. */
    val epithets: Array<EpithetChipContext>
    val hasEpithets: Boolean
    val isGM: Boolean
}

@Suppress("unused")
@JsPlainObject
external interface RenownCardContext {
    val pcs: Array<PcRenownContext>
    val hasAny: Boolean
    val isGM: Boolean
}

/** One PC as the card needs them, with everything the sheet already resolved. */
data class RenownCardPc(
    val actorUuid: String,
    val name: String,
    val roleLabel: String?,
    val isOwn: Boolean,
)

/**
 * Builds the Party-tab renown cards.
 *
 * [epithetLabel] and [factionLabel] are injected so the builder stays testable without i18n.
 * A PC with no ledger row still gets a card -- "no renown yet" is a true and useful thing to
 * show, and an absent card would read as the feature being broken for that player.
 */
fun buildRenownCardContext(
    pcs: List<RenownCardPc>,
    renownRows: Array<RawPcRenown>?,
    isGM: Boolean,
    epithetLabel: (String) -> String,
    factionLabel: (String, Int) -> String,
): RenownCardContext {
    val byUuid = (renownRows ?: emptyArray()).associateBy { it.actorUuid }
    val cards = pcs.map { pc ->
        val row = byUuid[pc.actorUuid]
        val visible = isGM || pc.isOwn
        val populace = row?.populace ?: 0
        PcRenownContext(
            actorUuid = pc.actorUuid,
            name = pc.name,
            roleLabel = pc.roleLabel,
            populace = populace.takeIf { visible },
            populacePct = (populace * 100 / MAX_POPULACE_RENOWN).coerceIn(0, 100).takeIf { visible },
            factionRenown = (row?.factionRenown ?: emptyArray())
                .mapNotNull { chip ->
                    val name = chip.factionName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val value = chip.renown ?: 0
                    FactionRenownChipContext(
                        factionName = name,
                        renown = value,
                        label = factionLabel(name, value),
                    )
                }
                .toTypedArray()
                .takeIf { visible },
            purchaseAccessTier = (row?.purchaseAccessTier ?: 0).takeIf { visible },
            hasNumbers = visible,
            epithets = (row?.epithets ?: emptyArray())
                .map { EpithetChipContext(id = it, label = epithetLabel(it)) }
                .toTypedArray(),
            hasEpithets = (row?.epithets?.size ?: 0) > 0,
            isGM = isGM,
        )
    }
    return RenownCardContext(
        pcs = cards.toTypedArray(),
        hasAny = cards.isNotEmpty(),
        isGM = isGM,
    )
}
