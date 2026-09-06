package at.posselt.pfrpg2e.camping

/**
 * Decides whether an actor's favorite meal should be marked as pinned ([fixedFavoriteMeal]).
 *
 * Rules:
 * - When [pickedMeal] has changed from [previousMeal]:
 *     - If a meal was picked ([pickedMeal] != null), hand-picking pins it (true).
 *     - If the pick was cleared ([pickedMeal] == null), it unpins (false).
 * - When [pickedMeal] has not changed:
 *     - If explicitly unpinned ([explicitlyUnpinned] == true), it unpins (false).
 *     - Otherwise, the previous pin state [wasPinned] is preserved unchanged.
 *
 * This guarantees that opening the favorite meals dialog and pressing Save does NOT
 * pin auto-learned meals, while allowing explicit unpinning and hand-picked pinning.
 */
fun shouldRowBePinned(
    wasPinned: Boolean,
    previousMeal: String?,
    pickedMeal: String?,
    explicitlyUnpinned: Boolean = false,
): Boolean {
    val changed = previousMeal != pickedMeal
    return when {
        changed -> pickedMeal != null
        explicitlyUnpinned -> false
        else -> wasPinned
    }
}
