package at.posselt.pfrpg2e.kingdom

import at.posselt.pfrpg2e.actor.isKingmakerInstalled
import at.posselt.pfrpg2e.data.kingdom.RealmData
import at.posselt.pfrpg2e.data.kingdom.RealmData.WorkSite
import at.posselt.pfrpg2e.data.kingdom.RealmData.WorkSites
import at.posselt.pfrpg2e.kingdom.scenes.GridType
import at.posselt.pfrpg2e.kingdom.scenes.Rectangle
import at.posselt.pfrpg2e.kingdom.scenes.toRectangle
import at.posselt.pfrpg2e.macros.RealmTileCategory
import at.posselt.pfrpg2e.macros.RealmTileType
import at.posselt.pfrpg2e.utils.asSequence
import at.posselt.pfrpg2e.utils.getRealmTileData
import com.foundryvtt.core.Game
import com.foundryvtt.core.documents.Scene
import com.foundryvtt.kingmaker.HexState
import com.foundryvtt.kingmaker.kingmaker
import js.array.component1
import js.array.component2

private fun parseKingmakerWorksite(
    hexes: List<HexState>,
    type: String,
    commodity: String
): WorkSite = hexes.asSequence()
    .filter { it.camp == type }
    .map {
        // There is no "luxury" camp on the Kingmaker map, so a mine on a Luxury Resource hex is
        // treated as a luxury source (counted, but it produces no Commodity here) and is excluded
        // from the ore-mine count.
        val (quantity, resources) = when {
            type == "mine" && commodity == "luxuries" ->
                (if (it.commodity == "luxuries") 1 else 0) to 0
            type == "mine" && it.commodity == "luxuries" ->
                0 to 0
            // RAW: an established Work Site generates 1 Commodity of its type, doubled to 2 when the
            // hex also has a matching Resource (the Kingmaker hex `commodity` marks that Resource).
            else ->
                1 to if (commodity == it.commodity) 2 else 1
        }
        WorkSite(
            quantity = quantity,
            resources = resources
        )
    }
    .fold(WorkSite()) { prev, curr -> prev + curr }


private fun parseKingmaker(): RealmData {
    val state = kingmaker.state
    val claimed = state.hexes.asSequence()
        .filter { (_, value) -> value.claimed == true }
        .map { it.component2() }
        .toList()
    val farms = claimed.filter { it.features?.any { f -> f.type == "farmland" } == true }.size
    val food = claimed.filter { it.commodity == "food" }.size
    return RealmData(
        size = claimed.size,
        worksites = WorkSites(
            farmlands = WorkSite(
                quantity = farms,
                resources = food,
            ),
            quarries = parseKingmakerWorksite(claimed, "quarry", "stone"),
            mines = parseKingmakerWorksite(claimed, "mine", "ore"),
            lumberCamps = parseKingmakerWorksite(claimed, "lumber", "lumber"),
            luxurySources = parseKingmakerWorksite(claimed, "mine", "luxuries"),
        )
    )
}

private data class TileAndPlacement(val type: RealmTileType, val rectangle: Rectangle)

private data class ResourceTile(val tile: TileAndPlacement, val claimed: TileAndPlacement)

private fun toRealmWorksite(
    resources: List<ResourceTile>,
    commoditiesInClaimedTile: Map<Rectangle, List<ResourceTile>>,
    type: RealmTileType,
    commodityType: RealmTileType,
    includeResources: Boolean = true,
): WorkSite = resources
    .filter { it.tile.type == type }
    .map { site ->
        WorkSite(
            quantity = 1,
            resources = if (includeResources) {
                commoditiesInClaimedTile[site.claimed.rectangle]
                    ?.count { it.tile.type == commodityType } ?: 0
            } else {
                0
            }
        )
    }
    .fold(WorkSite()) { prev, curr -> prev + curr }

private fun Scene.parseRealmData(actor: KingdomActor): RealmData {
    val gridType = GridType.HEX
    val tiles = tiles.contents
        .mapNotNull {
            it.getRealmTileData()
                ?.takeIf { it.kingdomActorUuid == null || it.kingdomActorUuid == actor.uuid }
                ?.let { RealmTileType.fromString(it.type) }
                ?.let { type -> TileAndPlacement(type = type, it.toRectangle(gridType)) }
        } + drawings.contents
        .mapNotNull {
            it.getRealmTileData()
                ?.takeIf { it.kingdomActorUuid == null || it.kingdomActorUuid == actor.uuid }
                ?.let { RealmTileType.fromString(it.type) }
                ?.let { type -> TileAndPlacement(type = type, it.toRectangle(gridType)) }
        }
    val claimed = tiles
        .filter { it.type == RealmTileType.CLAIMED }
        .map { it.copy(rectangle = it.rectangle.applyTolerance()) }
    val resources = tiles
        .asSequence()
        .filterNot { it.type == RealmTileType.CLAIMED }
        .mapNotNull { tile ->
            claimed
                .find { hex -> tile.rectangle in hex.rectangle }
                ?.let { ResourceTile(tile = tile, claimed = it) }
        }
        .toList()
    val commoditiesInClaimedTile = resources
        .filter { it.tile.type.category == RealmTileCategory.COMMODITY }
        .groupBy { it.claimed.rectangle }
    // food is not required to be in the same hex as a farmland
    val food = resources.count { it.tile.type == RealmTileType.FOOD }
    return RealmData(
        size = claimed.size,
        worksites = WorkSites(
            farmlands = toRealmWorksite(
                resources = resources,
                commoditiesInClaimedTile = commoditiesInClaimedTile,
                type = RealmTileType.FARMLAND,
                commodityType = RealmTileType.FOOD,
                includeResources = false,
            ).copy(resources = food),
            lumberCamps = toRealmWorksite(
                resources = resources,
                commoditiesInClaimedTile = commoditiesInClaimedTile,
                type = RealmTileType.LUMBER_CAMP,
                commodityType = RealmTileType.LUMBER
            ),
            mines = toRealmWorksite(
                resources = resources,
                commoditiesInClaimedTile = commoditiesInClaimedTile,
                type = RealmTileType.MINE,
                commodityType = RealmTileType.ORE
            ),
            quarries = toRealmWorksite(
                resources = resources,
                commoditiesInClaimedTile = commoditiesInClaimedTile,
                type = RealmTileType.QUARRY,
                commodityType = RealmTileType.STONE
            ),
            luxurySources = toRealmWorksite(
                resources = resources,
                commoditiesInClaimedTile = commoditiesInClaimedTile,
                type = RealmTileType.LUXURY_WORKSITE,
                commodityType = RealmTileType.LUXURY
            ),
        )
    )
}

private fun KingdomData.parseWorksites() =
    WorkSites(
        farmlands = WorkSite(
            quantity = workSites.farmlands.quantity,
            resources = workSites.farmlands.resources,
        ),
        lumberCamps = WorkSite(
            quantity = workSites.lumberCamps.quantity,
            resources = workSites.lumberCamps.resources,
        ),
        mines = WorkSite(
            quantity = workSites.mines.quantity,
            resources = workSites.mines.resources,
        ),
        quarries = WorkSite(
            quantity = workSites.quarries.quantity,
            resources = workSites.quarries.resources,
        ),
        luxurySources = WorkSite(
            quantity = workSites.luxurySources.quantity,
            resources = workSites.luxurySources.resources,
        ),
    )

fun Game.getRealmData(
    kingdomActor: KingdomActor,
    kingdom: KingdomData,
): RealmData {
    val mode = AutomateResources.fromString(kingdom.settings.automateResources)
    val realmScene = kingdom.settings.realmSceneId?.let { scenes.get(it) }
    return when (mode) {
        AutomateResources.KINGMAKER if isKingmakerInstalled -> parseKingmaker()
        AutomateResources.TILE_BASED if realmScene != null -> realmScene.parseRealmData(kingdomActor)
        else -> RealmData(size = kingdom.size, worksites = kingdom.parseWorksites())
    }
}

/**
 * A work site (or Farmland) that exists on a hex the kingdom hasn't claimed yet. Per the Kingmaker
 * rules a work site only produces Commodities once its hex is claimed, so these are surfaced on the
 * sheet as a reminder that claiming the hex will turn them on. Only meaningful in Kingmaker mode,
 * where each hex carries its own `camp`/`claimed` flags.
 */
data class UnclaimedWorksite(
    val camp: String,        // quarry | mine | lumber | farmland (the Kingmaker hex `camp`)
    val hexLabel: String,    // human coordinate, e.g. "4.21"
    val commodity: String?,  // stone | ore | lumber | food | luxuries
)

fun Game.getUnclaimedWorksites(kingdom: KingdomData): List<UnclaimedWorksite> {
    val mode = AutomateResources.fromString(kingdom.settings.automateResources)
    if (mode != AutomateResources.KINGMAKER || !isKingmakerInstalled) return emptyList()
    return kingmaker.state.hexes.asSequence()
        .filter { (_, hex) -> hex.claimed != true }
        .flatMap { (key, hex) ->
            val coord = key.toIntOrNull()?.let { "${it / 1000}.${it % 1000}" } ?: key
            val sites = mutableListOf<UnclaimedWorksite>()
            hex.camp?.takeIf { it.isNotBlank() }?.let { camp ->
                sites.add(UnclaimedWorksite(camp = camp, hexLabel = coord, commodity = hex.commodity))
            }
            if (hex.features?.any { it.type == "farmland" } == true) {
                sites.add(UnclaimedWorksite(camp = "farmland", hexLabel = coord, commodity = "food"))
            }
            sites.asSequence()
        }
        .toList()
}