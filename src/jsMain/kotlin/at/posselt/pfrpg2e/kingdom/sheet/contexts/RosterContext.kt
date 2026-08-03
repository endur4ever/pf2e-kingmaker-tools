package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.companion.CompanionPersonalQuest
import at.posselt.pfrpg2e.companion.clampInfluence
import at.posselt.pfrpg2e.companion.influenceBarPercent
import at.posselt.pfrpg2e.companion.normalizeDiscoveryStatus
import at.posselt.pfrpg2e.kingdom.data.RawCharacter
import at.posselt.pfrpg2e.kingdom.data.RawCompanionExpedition
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
    val level: Int
    val xp: Int
    val xpPercent: Int
    val expeditionStatus: String
    val expeditionStatusLabel: String
    val injuryDaysRemaining: Int?
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
    expeditions: Array<RawCompanionExpedition> = emptyArray(),
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
            val activeExp = if (character.expeditionStatus == "onExpedition") {
                expeditions.find { exp -> key in exp.companionIds && exp.status != "resolved" && exp.status != "cancelled" }
            } else {
                null
            }
            val expeditionStatusLabel = when (character.expeditionStatus) {
                "available" -> localize("kingdom.companion.expeditionStatus.available")
                "onExpedition" -> {
                    if (activeExp != null) {
                        val template = localize("kingdom.companion.expeditionStatus.onExpeditionDays")
                        if (template != "kingdom.companion.expeditionStatus.onExpeditionDays") {
                            template.replace("{title}", activeExp.title).replace("{days}", activeExp.daysRemaining.toString())
                        } else {
                            "${activeExp.title}, returns in ${activeExp.daysRemaining}d"
                        }
                    } else {
                        localize("kingdom.companion.expeditionStatus.onExpedition")
                    }
                }
                "unavailable" -> {
                    val days = character.injuryDaysRemaining
                    if (days != null) {
                        val template = localize("kingdom.companion.expeditionStatus.recoveringDays")
                        if (template != "kingdom.companion.expeditionStatus.recoveringDays") {
                            template.replace("{days}", days.toString())
                        } else {
                            localize("kingdom.companion.expeditionStatus.unavailable")
                        }
                    } else {
                        localize("kingdom.companion.expeditionStatus.unavailable")
                    }
                }
                else -> localize("kingdom.companion.expeditionStatus.${character.expeditionStatus}")
            }
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
                level = character.level,
                xp = character.xp,
                xpPercent = (character.xp % 1000) / 10,
                expeditionStatus = character.expeditionStatus,
                expeditionStatusLabel = expeditionStatusLabel,
                injuryDaysRemaining = character.injuryDaysRemaining,
            )
        }.toTypedArray(),
        isGM = isGM,
    )

