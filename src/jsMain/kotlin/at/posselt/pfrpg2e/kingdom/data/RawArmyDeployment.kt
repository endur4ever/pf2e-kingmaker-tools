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

    /**
     * Seeded by Migration41 but never declared until now, so the migration wrote into a slot
     * nothing could read through the typed API.
     *
     * Neither field has a consumer: the Deploy Army mishap tracking they were meant to serve was
     * ultimately built on a flag on the army ACTOR instead (see DeployArmyOfferCard's
     * appliedDeployKeys). They are declared rather than dropped because existing worlds already
     * carry them, and an undeclared persisted field is exactly the trap this fixes.
     */
    var checkResult: String?
    var effectsApplied: Boolean?
}
