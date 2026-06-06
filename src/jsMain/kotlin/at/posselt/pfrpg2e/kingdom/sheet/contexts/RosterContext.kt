package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.companion.clampInfluence
import at.posselt.pfrpg2e.companion.influenceBarPercent
import at.posselt.pfrpg2e.companion.normalizeDiscoveryStatus
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RosterActorContext {
    val name: String
    val role: String
    val roleLabel: String
    val speed: Int
    val destinationX: Int?
    val destinationY: Int?
    val destinationLabel: String
    val eta: Int?
    val traveling: Boolean
    val active: Boolean
    val plotHook: String?
    val actorUuid: String?
    val img: String?
    val influence: Int
    val influencePercent: Int
    val campAvailable: Boolean
    val discoveryStatus: String
    val discoveryStatusLabel: String
    val personalQuestCount: Int
    val hasPersonalQuests: Boolean
}

@JsPlainObject
external interface RosterContext {
    val items: Array<RosterActorContext>
    val isGM: Boolean
}

/**
 * Builds the roster context. Pure (no Foundry i18n) so it stays unit-testable; callers from the app
 * pass [localize] = the real localizer, tests use the identity default. [personalQuests] is the full
 * companionPersonalQuests array used to compute per-companion active-quest counts.
 */
fun Array<RawCharacter>.toRosterContext(
    isGM: Boolean,
    personalQuests: Array<CompanionPersonalQuest> = emptyArray(),
    localize: (String) -> String = { it },
): RosterContext =
    RosterContext(
        items = map { character ->
            val status = normalizeDiscoveryStatus(character.discoveryStatus)
            val key = character.actorUuid ?: character.name
            val ids = character.personalQuestIds.toSet()
            val activeCount = personalQuests.count {
                (it.id in ids || it.companionId == key) && it.status == "active"
            }
            val influence = clampInfluence(character.influence)
            RosterActorContext(
                name = character.name,
                role = character.role,
                roleLabel = if (character.role == "npc") "NPC" else "Companion",
                speed = character.speed,
                destinationX = character.destinationX,
                destinationY = character.destinationY,
                destinationLabel = if (character.destinationX != null && character.destinationY != null) {
                    "(${character.destinationX}, ${character.destinationY})"
                } else {
                    "-"
                },
                eta = character.eta,
                traveling = character.traveling,
                active = character.active,
                plotHook = character.plotHook,
                actorUuid = character.actorUuid,
                img = character.img,
                influence = influence,
                influencePercent = influenceBarPercent(influence),
                campAvailable = character.campAvailable,
                discoveryStatus = status,
                discoveryStatusLabel = localize("kingdom.companion.discovery.$status"),
                personalQuestCount = activeCount,
                hasPersonalQuests = activeCount > 0,
            )
        }.toTypedArray(),
        isGM = isGM,
    )
