package at.posselt.pfrpg2e.kingdom.sheet.contexts

import at.posselt.pfrpg2e.app.forms.CheckboxInput
import at.posselt.pfrpg2e.app.forms.FormElementContext
import at.posselt.pfrpg2e.app.forms.Select
import at.posselt.pfrpg2e.app.forms.TextInput
import at.posselt.pfrpg2e.data.kingdom.DEFAULT_FACTION_STANDING
import at.posselt.pfrpg2e.data.kingdom.Relations
import at.posselt.pfrpg2e.data.kingdom.attitudeFor
import at.posselt.pfrpg2e.kingdom.data.RawFactionStandingEntry
import at.posselt.pfrpg2e.kingdom.data.RawGroup
import at.posselt.pfrpg2e.kingdom.localizeAgendaArchetype
import at.posselt.pfrpg2e.kingdom.localizeAgendaGoal
import at.posselt.pfrpg2e.utils.t
import kotlinx.js.JsPlainObject

@Suppress("unused")
@JsPlainObject
external interface GroupContext {
    val name: FormElementContext
    val negotiationDC: FormElementContext
    val atWar: FormElementContext
    val preventPledgeOfFealty: FormElementContext
    val relations: FormElementContext
    val hexKey: FormElementContext
    /** The group's display name as a plain string; [name] is a form input context. */
    val plainName: String
    val attitude: String
    val standing: Int
    val allianceLevel: String?
    val standingLog: Array<RawFactionStandingEntry>?
    val agenda: AgendaCardContext?
}

@Suppress("unused")
@JsPlainObject
external interface AgendaCardContext {
    val goalLabel: String
    val progress: Int
    val segments: Int
    val progressPct: Int
    /** GM-only detail; null for players. */
    val archetypeLabel: String?
    val cooldownSummary: String?
}

fun Array<RawGroup>.toContext(isGM: Boolean = false) =
    mapIndexed { index, group ->
        GroupContext(
            name = TextInput(
                name = "groups.$index.name",
                label = t("applications.name"),
                hideLabel = true,
                value = group.name,
            ).toContext(),
            negotiationDC = Select.dc(
                name = "groups.$index.negotiationDC",
                label = t("kingdom.negotiationDc"),
                hideLabel = true,
                value = group.negotiationDC,
            ).toContext(),
            atWar = CheckboxInput(
                name = "groups.$index.atWar",
                label = t("kingdom.atWar"),
                hideLabel = true,
                value = group.atWar,
            ).toContext(),
            preventPledgeOfFealty = CheckboxInput(
                name = "groups.$index.preventPledgeOfFealty",
                label = t("kingdom.preventPledgeOfFealty"),
                hideLabel = true,
                value = group.preventPledgeOfFealty,
            ).toContext(),
            relations = Select.fromEnum<Relations>(
                name = "groups.$index.relations",
                hideLabel = true,
                value = Relations.fromString(group.relations) ?: Relations.NONE,
            ).toContext(),
            hexKey = TextInput(
                name = "groups.$index.hexKey",
                label = t("kingdom.caravans.partnerHex"),
                hideLabel = true,
                value = group.hexKey ?: "",
                required = false,
            ).toContext(),
            plainName = group.name,
            attitude = t(attitudeFor(group.standing).i18nKey),
            agenda = group.agenda?.let { agenda ->
                AgendaCardContext(
                    goalLabel = localizeAgendaGoal(agenda.goalId, agenda.goalTitle),
                    progress = agenda.progress,
                    segments = agenda.segments,
                    progressPct = if (agenda.segments > 0) {
                        (agenda.progress * 100 / agenda.segments).coerceIn(0, 100)
                    } else 0,
                    archetypeLabel = localizeAgendaArchetype(agenda.archetype).takeIf { isGM },
                    cooldownSummary = agenda.moveCooldowns?.let { cds ->
                        js.objects.Object.keys(cds.unsafeCast<Any>())
                            .joinToString(", ") { key -> "$key: ${cds[key]}" }
                    }?.takeIf { isGM && it.isNotBlank() },
                )
            },
            standing = group.standing ?: DEFAULT_FACTION_STANDING,
            allianceLevel = group.allianceLevel,
            standingLog = group.standingLog,
        )
    }.toTypedArray()