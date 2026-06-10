package at.posselt.pfrpg2e.data.kingdom.settlements

import at.posselt.pfrpg2e.data.kingdom.structures.AvailableItemBonuses
import at.posselt.pfrpg2e.data.kingdom.structures.CommodityStorage
import at.posselt.pfrpg2e.data.kingdom.structures.GroupedStructureBonus
import at.posselt.pfrpg2e.data.kingdom.structures.Structure
import kotlin.math.abs
import kotlin.math.max

data class Block(
    val delayedStructures: List<Structure>,
    val constructedStructures: List<Structure>,
    val structuresUnderConstruction: List<Structure>,
) {
    val occupiedLots = constructedStructures.sumOf { it.lots }
    val isOccupied = occupiedLots > 0
}

data class NpcEntry(
    val id: String,
    val name: String,
    val occupation: String,
    val notes: String? = null,
)

data class PopulationRoster(
    val npcs: List<NpcEntry> = emptyList(),
)

/**
 * Common occupations for generated NPCs in a Stolen Lands settlement.
 */
val npcOccupations = listOf(
    "Farmer", "Blacksmith", "Innkeeper", "Merchant", "Fisher",
    "Carpenter", "Tanner", "Miller", "Baker", "Butcher",
    "Healer", "Guard", "Tailor", "Brewer", "Herbalist",
    "Ranger", "Soldier", "Scribe", "Priest", "Teamster",
    "Potter", "Weaver", "Mason", "Fletcher", "Cobbler",
    "Barber", "Courier", "Lamplighter", "Tavern Keeper", "Rat Catcher",
    "Soapmaker", "Tinker", "Woodcutter", "Shepherd", "Beekeeper",
    "Jeweler", "Painter", "Ropewright", "Cartwright", "Miner",
)

/**
 * Deterministically seed an initial population of named NPCs for this settlement.
 *
 * Uses [size.populationNumber] to decide how many NPCs to create (a sampled
 * subset, not one per resident — roughly sqrt(population) * 1.5, capped at a
 * reasonable maximum).  The RNG is seeded from [id] so the same settlement
 * always produces the same roster.
 *
 * @return A new [PopulationRoster] with generated NPCs.
 */
fun Settlement.generateInitialPopulation(): PopulationRoster {
    if (populationRoster.npcs.isNotEmpty()) return populationRoster
    return PopulationRoster(npcs = generateNpcBatch(count = recommendedRosterSize(), existing = emptyList()))
}

/**
 * The roster size the generator aims for at this settlement's current population.
 * Scale: sqrt * 1.5 gives ~30 for a village of 400, ~67 for a town of 2000,
 * ~237 for a city of 25000.  Cap at 200 so very large settlements don't flood.
 */
fun Settlement.recommendedRosterSize(): Int =
    minOf(200, maxOf(5, (kotlin.math.sqrt(size.populationNumber.toDouble()) * 1.5).toInt()))

/**
 * Tops the roster up to [recommendedRosterSize] after the settlement's population has
 * grown. Existing entries — including user-edited and user-added ones — are never
 * touched, and NPCs the user deleted are not resurrected (the new batch is salted by
 * the current roster size, so it differs from the names dealt at seeding). Returns the
 * roster unchanged when it is already at or above the recommended size; regeneration is
 * never automatic — callers invoke this from an explicit user action.
 */
fun Settlement.growPopulation(): PopulationRoster {
    val target = recommendedRosterSize()
    val current = populationRoster.npcs
    if (current.size >= target) return populationRoster
    return PopulationRoster(npcs = current + generateNpcBatch(count = target - current.size, existing = current))
}

private fun Settlement.generateNpcBatch(count: Int, existing: List<NpcEntry>): List<NpcEntry> {
    // Salt with the existing roster size so growth batches differ from the seed batch.
    val seed = id.fold(0L) { acc, c -> acc * 31 + c.code } + existing.size * 7919L
    val rng = SeededRng(seed)
    val gen = NpcNameGenerator(seed)
    val existingIds = existing.map { it.id }.toMutableSet()
    var index = existing.size
    return List(count) {
        var npcId = "npc-${id}-$index"
        while (npcId in existingIds) {
            index++
            npcId = "npc-${id}-$index"
        }
        existingIds.add(npcId)
        index++
        NpcEntry(
            id = npcId,
            name = gen.generate().fullName,
            occupation = npcOccupations[rng.nextInt(npcOccupations.size)],
        )
    }
}

data class Settlement(
    val id: String,
    val name: String,
    val type: SettlementType,
    val waterBorders: Int,
    val isSecondaryTerritory: Boolean,
    val settlementEventBonus: Int, // added by watchtowers
    val leaderLeadershipActivityBonus: Int, // added by Palace & co
    val bonuses: Set<GroupedStructureBonus>,
    val allowCapitalInvestment: Boolean,
    val notes: Set<String>,
    val storage: CommodityStorage,
    val increaseLeadershipActivities: Boolean,
    val consumptionReduction: Int,
    val availableItems: AvailableItemBonuses,
    val size: SettlementSize,
    val unlockActivities: Set<String>,
    val residentialLots: Int,
    val hasBridge: Boolean,
    val occupiedBlocks: Int,
    val preventItemLevelPenalty: Boolean,
    val delayedStructures: List<Structure>,
    val constructedStructures: List<Structure>,
    val structuresUnderConstruction: List<Structure>,
    val maximumCivicRdLimit: Int,
    val settlementActions: Int,
    val blocks: List<Block>,
    val layoutType: SettlementLayoutType,
    val magicalStreetlamps: Boolean = false,
    val pavedStreets: Boolean = false,
    val sewerSystem: Boolean = false,
    val lotsBorderingWater: Int = 0,
    val edges: SettlementEdges = SettlementEdges(),
    val urbanGrid: UrbanGrid = UrbanGrid(),
    val populationRoster: PopulationRoster = PopulationRoster(),
) {
    private val totalConsumption = size.consumption - consumptionReduction
    val isOvercrowded = occupiedBlocks > residentialLots
    val lacksBridge = waterBorders >= 4 && !hasBridge
    val consumption = max(0, totalConsumption)
    val consumptionSurplus = totalConsumption
        .takeIf { it < 0 }
        ?.let { abs(it) }
        ?: 0
    val highestUniqueBonuses: Set<GroupedStructureBonus>
        get() {
            val bonusesByType = bonuses.groupBy { it.skill to it.activity }
            return bonusesByType
                .values.map { structures ->
                    structures.maxBy { it.value }
                }
                .toSet()
        }
    val level = max(1, occupiedBlocks)

    val itemPurchaseLevel: Int
        get() = when (size.type) {
            SettlementSizeType.VILLAGE -> 1
            SettlementSizeType.TOWN -> 3
            SettlementSizeType.CITY -> 9
            SettlementSizeType.METROPOLIS -> 15
        }

    val trainers: List<String>
        get() {
            val baseIds = constructedStructures.map { it.id.removeSuffix("-vk") }.toSet()
            val list = mutableListOf<String>()
            if ("shrine" in baseIds) {
                list.addAll(listOf("cleric", "oracle"))
            }
            if ("library" in baseIds) {
                list.addAll(listOf("investigator", "thaumaturge", "psychic"))
            }
            if ("alchemy-laboratory" in baseIds) {
                list.addAll(listOf("alchemist", "gunslinger", "inventor"))
            }
            if (baseIds.any { it.startsWith("tavern-") }) {
                list.add("bard")
            }
            if ("arcanists-tower" in baseIds) {
                list.addAll(listOf("wizard", "witch", "sorcerer", "magus"))
            }
            if ("garrison" in baseIds) {
                list.addAll(listOf("fighter", "barbarian", "champion", "monk"))
            }
            if ("sacred-grove" in baseIds) {
                list.addAll(listOf("druid", "kineticist", "summoner", "ranger"))
            }
            if ("thieves-guild" in baseIds) {
                list.add("rogue")
            }
            if ("pier" in baseIds) {
                list.add("swashbuckler")
            }
            return list.distinct()
        }

    val craftingAccess: List<String>
        get() {
            val baseIds = constructedStructures.map { it.id.removeSuffix("-vk") }.toSet()
            val list = mutableListOf<String>()
            if ("smithy" in baseIds || "foundry" in baseIds) {
                list.add("metallic")
            }
            if ("stonemason" in baseIds) {
                list.add("runes")
            }
            if ("tannery" in baseIds) {
                list.add("leather")
            }
            if ("arcanists-tower" in baseIds) {
                list.add("scrollsWandsStaves")
            }
            if ("luxury-store" in baseIds) {
                list.add("amuletsRings")
            }
            if ("library" in baseIds) {
                list.add("tomes")
            }
            if ("alchemy-laboratory" in baseIds) {
                list.add("alchemical")
            }
            if ("lumberyard" in baseIds) {
                list.add("wooden")
            }
            if ("specialized-artisan" in baseIds) {
                list.add("other")
            }
            return list.distinct()
        }

    fun canLevelUp(kingdomLevel: Int, capitalCanGrowOneSizeLarger: Boolean): SettlementLevelUpType? {
        // you can never level up if the settlement is overcrowded
        if (isOvercrowded) return null
        val isCapitalAndLarger = capitalCanGrowOneSizeLarger && type == SettlementType.CAPITAL
        val satisfiesTown = kingdomLevel >= 3 || isCapitalAndLarger
        val satisfiesCity = kingdomLevel >= 9 || (kingdomLevel >= 3 && isCapitalAndLarger)
        val satisfiesMetropolis = kingdomLevel >= 15 || (kingdomLevel >= 9 && isCapitalAndLarger)
        val blocksTotal = blocks.size
        return if (blocksTotal == 1 && blocks.sumOf { it.occupiedLots } == 4 && satisfiesTown) {
            SettlementLevelUpType.TOWN
        } else {
            val blocksWithAtLeast2OccupiedLots = blocks.filter { it.occupiedLots >= 2 }.size
            if (blocksTotal == 4 && blocksWithAtLeast2OccupiedLots == 4 && satisfiesCity) {
                SettlementLevelUpType.CITY
            } else if (blocksTotal == 9 && blocksWithAtLeast2OccupiedLots == 9 && satisfiesMetropolis) {
                SettlementLevelUpType.METROPOLIS
            } else if (blocksTotal == 18 && blocksWithAtLeast2OccupiedLots == 18 && satisfiesMetropolis) {
                SettlementLevelUpType.METROPOLIS_THIRD_GRID
            } else if (blocksTotal == 27 && blocksWithAtLeast2OccupiedLots == 27 && satisfiesMetropolis) {
                SettlementLevelUpType.METROPOLIS_FOURTH_GRID
            } else {
                null
            }
        }
    }

    fun nextLevelUp(): SettlementLevelUpType? = when(blocks.size) {
        1 -> SettlementLevelUpType.TOWN
        4 -> SettlementLevelUpType.CITY
        9 -> SettlementLevelUpType.METROPOLIS
        18 -> SettlementLevelUpType.METROPOLIS_THIRD_GRID
        27 -> SettlementLevelUpType.METROPOLIS_FOURTH_GRID
        else -> null
    }
}
