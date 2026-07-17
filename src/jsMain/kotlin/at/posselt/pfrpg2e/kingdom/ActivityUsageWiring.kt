package at.posselt.pfrpg2e.kingdom

/**
 * Conversions between the persisted [RawActivityBlock] array and the pure [ActivityUsage] model
 * used by the timeout/escalating-DC logic in commonMain.
 */

fun Array<RawActivityBlock>?.toActivityUsages(): List<ActivityUsage> =
    this.orEmpty().map {
        ActivityUsage(
            activityId = it.activityId,
            lockedUntilTurn = it.lockedUntilTurn,
            dcBump = it.dcBump ?: 0,
            usedThisTurn = it.usedThisTurn ?: false,
        )
    }

fun List<ActivityUsage>.toRawActivityBlocks(): Array<RawActivityBlock> =
    map {
        RawActivityBlock(
            activityId = it.activityId,
            lockedUntilTurn = it.lockedUntilTurn,
            dcBump = it.dcBump,
            usedThisTurn = it.usedThisTurn,
        )
    }.toTypedArray()

/** Live per-activity usage state on this kingdom (empty when never populated). */
fun KingdomData.activityUsages(): List<ActivityUsage> = activityUsage.toActivityUsages()
