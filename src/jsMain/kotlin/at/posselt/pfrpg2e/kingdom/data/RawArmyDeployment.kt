package at.posselt.pfrpg2e.kingdom.data

import kotlinx.js.JsPlainObject

/**
 * A player army deployed to counter a threat or garrison a settlement (roadmap #12).
 * Links to the Foundry PF2EArmy actor via [armyActorUuid]; name/type are cached for display.
 */
@JsPlainObject
external interface RawArmyDeployment {
    var id: String
    var armyActorUuid: String
    var armyName: String
    var armyType: String

    var assignedThreatId: String?
    var garrisonedSettlementId: String?
    var status: String

    var deployedTurn: Int
}
