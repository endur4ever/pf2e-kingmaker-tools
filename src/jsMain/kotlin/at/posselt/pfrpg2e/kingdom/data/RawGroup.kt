package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

@JsPlainObject
external interface RawGroup {
    var name: String
    var negotiationDC: Int
    var atWar: Boolean
    var preventPledgeOfFealty: Boolean
    var relations: String  // none, diplomatic-relations, trade-agreement

    // Faction & diplomacy relations tracker (nullable for back-compat with pre-existing
    // groups). `standing` is the numeric attitude (null => Indifferent); `standingLog` is
    // the append-only change history; `allianceLevel` is an optional treaty tier
    // (null | non-aggression | alliance | tribute).
    var standing: Int?
    var standingLog: Array<RawFactionStandingEntry>?
    var allianceLevel: String?

    // Realm-map hex this faction's trade hub sits on, for caravan routing (null => no location set).
    var hexKey: String?

    // Faction agenda engine (nullable for back-compat): null = idle, no agenda yet.
    var agenda: RawFactionAgenda?
}