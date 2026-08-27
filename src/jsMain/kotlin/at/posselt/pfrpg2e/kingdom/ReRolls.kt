package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.data.kingdom.leaders.Leader
import at.posselt.pfrpg2e.data.checks.DegreeOfSuccess
import at.posselt.pfrpg2e.data.checks.RollMode
import at.posselt.pfrpg2e.data.kingdom.KingdomSkill
import at.posselt.pfrpg2e.kingdom.dialogs.DegreeMessages
import at.posselt.pfrpg2e.kingdom.dialogs.rollCheck
import at.posselt.pfrpg2e.kingdom.modifiers.DowngradeResult
import at.posselt.pfrpg2e.kingdom.modifiers.Note
import at.posselt.pfrpg2e.kingdom.modifiers.UpgradeResult
import at.posselt.pfrpg2e.utils.deserializeB64Json
import at.posselt.pfrpg2e.utils.fromUuidTypeSafe
import at.posselt.pfrpg2e.utils.serializeB64Json
import at.posselt.pfrpg2e.utils.t
import at.posselt.pfrpg2e.utils.tpl
import kotlinx.js.JsPlainObject
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList
import org.w3c.dom.get
import kotlin.collections.isNotEmpty
import kotlin.text.toInt


@JsPlainObject
external interface UpOrDowngrade {
    val degree: String
    val times: Int
}


@Suppress("unused")
@JsPlainObject
external interface RollMetaContext {
    val label: String
    val dc: Int
    val skill: String
    val activityId: String?
    val eventId: String?
    val eventStageIndex: Int
    val actorUuid: String
    val degree: String
    val rollMode: String
    val modifier: Int
    val pills: Array<String>
    val creativeSolutionPills: Array<String>
    val fortune: Boolean
    val modifierWithCreativeSolution: Int
    val modifierWithoutFreeAndFair: Int
    val upgrades: String?
    val downgrades: String?
    val additionalChatMessages: String?
    val pillMode: String
    val notes: String?
    val eventIndex: Int
    val freeAndFairPills: Array<String>
    /** Renown attribution, carried through the chat card so a RE-ROLL can replace the deed it
     *  originally credited instead of double-crediting or silently crediting nobody. */
    val renownDeedId: String?
    val renownActorUuid: String?
    val renownActorName: String?
    val renownRole: String?
    val renownFactionName: String?
}

private fun parseRollMeta(rollElement: HTMLElement): RollMetaContext {
    val meta = rollElement.querySelector(".km-roll-meta") as HTMLElement?
    val creativePills = rollElement.querySelectorAll(".km-modifier-pill.km-creative-solution-pills").asList()
        .map { it.textContent ?: "" }
        .toTypedArray()
    val pills = rollElement.querySelectorAll(".km-modifier-pill.km-default-pills").asList()
        .map { it.textContent ?: "" }
        .toTypedArray()
    val freeAndFairPills = rollElement.querySelectorAll(".km-modifier-pill.km-free-and-fair-pills").asList()
        .map { it.textContent ?: "" }
        .toTypedArray()
    return RollMetaContext(
        label = meta?.dataset["label"] ?: "",
        dc = meta?.dataset["dc"]?.toInt() ?: 0,
        activityId = meta?.dataset["activityId"] ?: "",
        actorUuid = meta?.dataset["kingdomActorUuid"] ?: "",
        degree = meta?.dataset["degree"] ?: "",
        rollMode = meta?.dataset["rollMode"] ?: "",
        modifier = meta?.dataset["modifier"]?.toInt() ?: 0,
        skill = meta?.dataset["skill"] ?: "",
        pills = pills,
        additionalChatMessages = meta?.dataset["additionalChatMessages"],
        renownDeedId = meta?.dataset["renownDeedId"]?.takeIf { it.isNotBlank() },
        renownActorUuid = meta?.dataset["renownActorUuid"]?.takeIf { it.isNotBlank() },
        renownActorName = meta?.dataset["renownActorName"]?.takeIf { it.isNotBlank() },
        renownRole = meta?.dataset["renownRole"]?.takeIf { it.isNotBlank() },
        renownFactionName = meta?.dataset["renownFactionName"]?.takeIf { it.isNotBlank() },
        upgrades = meta?.dataset["upgrades"],
        downgrades = meta?.dataset["downgrades"],
        creativeSolutionPills = creativePills,
        freeAndFairPills = freeAndFairPills,
        fortune = meta?.dataset["fortune"] == "true",
        modifierWithCreativeSolution = meta?.dataset["modifierWithCreativeSolution"]?.toInt() ?: 0,
        modifierWithoutFreeAndFair = meta?.dataset["modifierWithoutFreeAndFair"]?.toInt() ?: 0,
        pillMode = "default",
        notes = meta?.dataset["notes"],
        eventStageIndex = meta?.dataset["eventStageIndex"]?.toInt() ?: 0,
        eventId = meta?.dataset["eventId"],
        eventIndex = meta?.dataset["eventIndex"]?.toInt() ?: 0,
    )
}

suspend fun generateRollMeta(
    activity: RawActivity?,
    modifier: Int,
    modifierPills: Array<String>,
    actor: KingdomActor,
    rollMode: RollMode,
    degree: DegreeOfSuccess,
    skill: KingdomSkill,
    dc: Int,
    fortune: Boolean,
    creativeSolutionPills: Array<String>,
    freeAndFairPills: Array<String>,
    modifierWithCreativeSolution: Int,
    modifierWithoutFreeAndFair: Int,
    isCreativeSolution: Boolean,
    isFreeAndFair: Boolean,
    additionalChatMessages: DegreeMessages?,
    upgrades: Set<UpgradeResult>,
    downgrades: Set<DowngradeResult>,
    notes: Set<Note>,
    eventId: String?,
    eventStageIndex: Int,
    eventIndex: Int,
    renownDeedId: String? = null,
    renownActorUuid: String? = null,
    renownActorName: String? = null,
    renownRole: String? = null,
    renownFactionName: String? = null,
): String {
    val upgradeData = upgrades
        .map { UpOrDowngrade(degree = it.upgrade.value, times = it.times) }
        .toTypedArray()
    val downgradeData = downgrades
        .map { UpOrDowngrade(degree = it.downgrade.value, times = it.times) }
        .toTypedArray()

    return tpl(
        path = "chatmessages/roll-flavor.hbs",
        ctx = RollMetaContext(
            label = activity?.title ?: t(skill),
            dc = dc,
            activityId = activity?.id,
            actorUuid = actor.uuid,
            degree = degree.value,
            rollMode = rollMode.value,
            modifier = modifier,
            fortune = fortune,
            skill = skill.value,
            additionalChatMessages = additionalChatMessages?.let { serializeB64Json(it) },
            upgrades = upgradeData.takeIf { it.isNotEmpty() }?.let { serializeB64Json(it) },
            downgrades = downgradeData.takeIf { it.isNotEmpty() }?.let { serializeB64Json(it) },
            modifierWithCreativeSolution = modifierWithCreativeSolution,
            pills = modifierPills,
            creativeSolutionPills = creativeSolutionPills,
            freeAndFairPills = freeAndFairPills,
            pillMode = if(isCreativeSolution) {
                "creativeSolution"
            } else if (isFreeAndFair) {
                "freeAndFair"
            } else {
                "default"
            },
            notes = serializeB64Json(notes.map { it.serialize() }.toTypedArray()),
            eventId = eventId,
            eventStageIndex = eventStageIndex,
            eventIndex = eventIndex,
            renownDeedId = renownDeedId,
            renownActorUuid = renownActorUuid,
            renownActorName = renownActorName,
            renownRole = renownRole,
            renownFactionName = renownFactionName,
            modifierWithoutFreeAndFair = modifierWithoutFreeAndFair,
        ),
    )
}

enum class ReRollMode {
    ROLL_TWICE_KEEP_HIGHEST,
    ROLL_TWICE_KEEP_LOWEST,
    DEFAULT,
    FAME_OR_INFAMY,
    FREE_AND_FAIR,
    CREATIVE_SOLUTION;
}

suspend fun reRoll(chatMessage: HTMLElement, mode: ReRollMode) {
    val meta = parseRollMeta(chatMessage)
    val rollMode = RollMode.fromString(meta.rollMode) ?: return
    val actor = fromUuidTypeSafe<KingdomActor>(meta.actorUuid) ?: return
    val kingdom = actor.getKingdom() ?: return
    val skill = KingdomSkill.fromString(meta.skill) ?: return
    val activity = meta.activityId?.let { kingdom.getActivity(it) }
    val event = meta.eventId?.let { kingdom.getEvent(it) }
    val eventStageIndex = meta.eventStageIndex
    val eventIndex = meta.eventIndex
    val upgrades = meta.upgrades?.let { deserializeB64Json<Array<UpOrDowngrade>>(it) }.orEmpty()
    val downgrades = meta.downgrades?.let { deserializeB64Json<Array<UpOrDowngrade>>(it) }.orEmpty()
    val degreeMessages = meta.additionalChatMessages?.let { deserializeB64Json<DegreeMessages>(it) }
    val notes = meta.notes?.let { deserializeB64Json<Array<RawNote>>(it).map { it.parse() }.toSet() } ?: emptySet()
    rollCheck(
        afterRoll = {},
        rollMode = rollMode,
        activity = activity,
        skill = skill,
        modifier = meta.modifier,
        modifierWithCreativeSolution = meta.modifierWithCreativeSolution,
        modifierWithoutFreeAndFair = meta.modifierWithoutFreeAndFair,
        fortune = meta.fortune,
        modifierPills = meta.pills,
        dc = meta.dc,
        kingdomActor = actor,
        upgrades = upgrades.mapNotNull { result ->
            DegreeOfSuccess.fromString(result.degree)?.let { degree ->
                UpgradeResult(upgrade = degree, times = result.times)
            }
        }.toSet(),
        isFreeAndFair = mode == ReRollMode.FREE_AND_FAIR,
        rollTwiceKeepHighest = mode == ReRollMode.ROLL_TWICE_KEEP_HIGHEST,
        rollTwiceKeepLowest = mode == ReRollMode.ROLL_TWICE_KEEP_LOWEST,
        creativeSolutionPills = meta.creativeSolutionPills,
        isCreativeSolution = mode == ReRollMode.CREATIVE_SOLUTION,
        downgrades = downgrades.mapNotNull { result ->
            DegreeOfSuccess.fromString(result.degree)?.let { degree ->
                DowngradeResult(downgrade = degree, times = result.times)
            }
        }.toSet(),
        degreeMessages = degreeMessages,
        // the identity the ORIGINAL roll minted, so this re-roll replaces that deed rather than
        // crediting a second one (or, without it, crediting nobody and leaving the failed
        // original standing in the ledger)
        leaderActorUuid = meta.renownActorUuid,
        leaderActorName = meta.renownActorName,
        leaderRole = meta.renownRole?.let { Leader.fromString(it) },
        factionName = meta.renownFactionName,
        deedId = meta.renownDeedId,
        useFameInfamy = mode == ReRollMode.FAME_OR_INFAMY,
        assurance = false,
        notes = notes,
        event = event?.parse(),
        eventStageIndex = eventStageIndex,
        eventIndex = eventIndex,
        freeAndFairPills = meta.freeAndFairPills,
    )
}