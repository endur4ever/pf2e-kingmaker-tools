package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.companion.MAX_COMPANION_INFLUENCE
import at.posselt.pfrpg2e.companion.clampInfluence
import at.posselt.pfrpg2e.companion.influenceBarPercent
import at.posselt.pfrpg2e.kingdom.data.RawPartyMemberInfluence
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface PartyMemberInfluenceContext {
    val uuid: String
    val name: String
    val img: String?
    val influence: Int
    val influencePercent: Int
    val maxInfluence: Int
}

@JsPlainObject
external interface CompanionInfluenceGroupContext {
    val companionId: String
    val name: String
    val img: String?
    val roleLabel: String
    val members: Array<PartyMemberInfluenceContext>
}

@JsPlainObject
external interface PartyInfluenceContext {
    val companions: Array<CompanionInfluenceGroupContext>
    val maxInfluence: Int
    val isGM: Boolean
}

/** Identity of one companion/NPC parent row, read live from the kingdom roster. */
class CompanionRef(
    val companionId: String,
    val name: String,
    val img: String?,
    val roleLabel: String,
)

/** Identity of one party member, read live from the PF2EParty actor. */
class PartyMemberRef(
    val uuid: String,
    val name: String,
    val img: String?,
)

/**
 * Pure builder for the Party Influence matrix. Each [companions] entry becomes a parent group; under
 * it, every party member in [members] gets their own influence value, looked up from [stored] by the
 * (companionId, memberUuid) pair and defaulting to 0. Stored rows whose companion or member is no
 * longer present are dropped. Influence reuses the companion 0-12 scale so bars read identically
 * across the sheet. Kept free of Foundry types so it stays unit-testable from commonTest.
 */
fun buildPartyInfluenceContext(
    companions: List<CompanionRef>,
    members: List<PartyMemberRef>,
    stored: Array<RawPartyMemberInfluence>,
    isGM: Boolean,
): PartyInfluenceContext {
    val byPair = stored.associate { (it.companionId to it.uuid) to it.influence }
    return PartyInfluenceContext(
        companions = companions.map { companion ->
            CompanionInfluenceGroupContext(
                companionId = companion.companionId,
                name = companion.name,
                img = companion.img,
                roleLabel = companion.roleLabel,
                members = members.map { member ->
                    val influence = clampInfluence(byPair[companion.companionId to member.uuid] ?: 0)
                    PartyMemberInfluenceContext(
                        uuid = member.uuid,
                        name = member.name,
                        img = member.img,
                        influence = influence,
                        influencePercent = influenceBarPercent(influence),
                        maxInfluence = MAX_COMPANION_INFLUENCE,
                    )
                }.toTypedArray(),
            )
        }.toTypedArray(),
        maxInfluence = MAX_COMPANION_INFLUENCE,
        isGM = isGM,
    )
}

/**
 * Returns a new array with the ([companionId], [uuid]) pair's influence set to clamp([value]),
 * upserting the entry if it does not exist yet. Immutable: the receiver is not modified.
 */
fun Array<RawPartyMemberInfluence>.withInfluence(
    companionId: String,
    uuid: String,
    value: Int,
): Array<RawPartyMemberInfluence> {
    val clamped = clampInfluence(value)
    val others = filter { !(it.companionId == companionId && it.uuid == uuid) }
    return (others + RawPartyMemberInfluence(companionId = companionId, uuid = uuid, influence = clamped))
        .toTypedArray()
}
