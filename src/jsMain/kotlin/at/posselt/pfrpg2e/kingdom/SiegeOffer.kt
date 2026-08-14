package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.kingdom.structures.StructureActor
import at.posselt.pfrpg2e.kingdom.structures.parseStructure
import at.posselt.pfrpg2e.kingdom.data.RawWarThreat
import at.posselt.pfrpg2e.utils.postChatTemplate
import at.posselt.pfrpg2e.utils.t
import com.foundryvtt.core.Game
import com.foundryvtt.core.game
import js.objects.recordOf

/** A structure standing in a settlement, as a siege sees it. */
data class SiegeTarget(
    /** The token's id — the stable handle a ruin is recorded against. */
    val tokenId: String,
    val name: String,
    /** The structure definition id, used to spot defences. */
    val structureId: String,
)

/**
 * The structures a siege could hit in the settlement on [sceneId], excluding any already ruined.
 *
 * Keyed on token id rather than structure id because a settlement can hold several copies of the
 * same structure, and razing one must not raze its twins.
 */
fun siegeTargetsFor(game: Game, kingdom: KingdomData, sceneId: String): List<SiegeTarget> {
    val scene = game.scenes.get(sceneId) ?: return emptyList()
    val alreadyRuined = kingdom.settlements
        .find { it.sceneId == sceneId }
        ?.destroyedStructureIds
        ?.toSet()
        ?: emptySet()
    return scene.tokens.contents
        .asSequence()
        .filter { it._id !in alreadyRuined && !it.hidden }
        .mapNotNull { token ->
            val actor = token.actor
            if (actor !is StructureActor) return@mapNotNull null
            val structure = actor.parseStructure() ?: return@mapNotNull null
            SiegeTarget(tokenId = token._id, name = structure.name, structureId = structure.id)
        }
        .toList()
}

/** What a sack of this settlement would cost, and which structures it would take. */
data class SiegePlan(
    val damage: SiegeDamage,
    val selection: List<SiegeTarget>,
    val defensiveStructures: Int,
    val availableStructures: Int,
    val garrisonPresent: Boolean,
)

/**
 * Rolls a concrete sack: how much damage the threat does and WHICH structures fall.
 *
 * The selection is random because a sack is, but it is rolled here and shown on the offer card so
 * the GM approves a specific list rather than a number — and can reroll it.
 */
fun planSiege(game: Game, kingdom: KingdomData, threat: RawWarThreat): SiegePlan? {
    val sceneId = threat.targetSettlementSceneId ?: return null
    val targets = siegeTargetsFor(game, kingdom, sceneId)
    val defensive = countDefensiveStructures(targets.map { it.structureId })
    // An army garrisoned in the settlement spares one more structure on top of the walls.
    val garrisonPresent = garrisonedArmyIdsFor(
        settlementId = sceneId,
        assignments = (kingdom.armyDeployments ?: emptyArray()).map {
            GarrisonAssignment(armyId = it.armyActorUuid, garrisonedSettlementId = it.garrisonedSettlementId)
        },
    ).isNotEmpty()
    val damage = siegeDamageWithGarrison(
        threatEscalation = threat.escalationLevel,
        maxEscalation = threat.maxEscalation,
        defensiveStructureCount = defensive,
        garrisonPresent = garrisonPresent,
    )
    return SiegePlan(
        damage = damage,
        selection = targets.shuffled().take(damage.structuresDestroyed),
        defensiveStructures = defensive,
        availableStructures = targets.size,
        garrisonPresent = garrisonPresent,
    )
}

/**
 * Posts the GM-whispered arrival offer for [threat], including siege options when it targets a
 * settlement.
 *
 * Shared by the End-Turn tick and the reroll button so a rerolled card is built exactly like the
 * original — the selection is the only thing that changes.
 */
suspend fun postWarThreatArrivalOffer(
    game: Game,
    actorUuid: String,
    kingdom: KingdomData,
    threat: RawWarThreat,
    gmUserIds: Array<String>,
) {
    if (gmUserIds.isEmpty()) return
    val plan = planSiege(game, kingdom, threat)
    val context = js("{}")
    context.threatId = threat.id
    context.threatName = threat.name
    context.actorUuid = actorUuid
    context.hasLinkedHex = kingdom.hexContents?.any { it.linkedWarThreatId == threat.id } == true
    context.hasSiege = plan != null && plan.availableStructures > 0
    if (plan != null) {
        context.siegeUnrest = plan.damage.unrestGain
        context.siegeCount = plan.damage.structuresDestroyed
        context.siegeDefences = plan.defensiveStructures
        context.siegeGarrison = plan.garrisonPresent
        context.siegeTokenIds = plan.selection.joinToString(",") { it.tokenId }
        context.siegeNames = plan.selection.joinToString(", ") { it.name }
        context.siegeSpared = plan.damage.structuresDestroyed == 0
        context.siegeSummary = if (plan.damage.structuresDestroyed == 0) {
            t(
                "chatMessages.siege.spared",
                recordOf("defences" to plan.defensiveStructures.toString(), "unrest" to plan.damage.unrestGain.toString()),
            )
        } else {
            t(
                "chatMessages.siege.willRaze",
                recordOf(
                    "names" to plan.selection.joinToString(", ") { it.name },
                    "unrest" to plan.damage.unrestGain.toString(),
                    "defences" to plan.defensiveStructures.toString(),
                ),
            )
        }
    }
    postChatTemplate(
        templatePath = "chatmessages/war-threat-arrival-offer.hbs",
        templateContext = context,
        whisper = gmUserIds,
    )
}

/** Convenience for callers that already hold the kingdom actor. */
suspend fun postWarThreatArrivalOffer(
    actor: KingdomActor,
    kingdom: KingdomData,
    threat: RawWarThreat,
) = postWarThreatArrivalOffer(
    game = game,
    actorUuid = actor.uuid,
    kingdom = kingdom,
    threat = threat,
    gmUserIds = game.users.filter { it.isGM }.mapNotNull { it.id }.toTypedArray(),
)
