package at.posselt.pfrpg2e.camping

/**
 * Stock offered by a MERCHANT-category encounter (roadmap #11).
 */
data class MerchantStock(
    val name: String,
    val stockItems: List<StockItem> = emptyList(),
    val stockRemaining: Int = 0,
    val stockMax: Int = 0,
    val greeting: String? = null,
    val region: String? = null,
    val uuid: String = "",
)

data class StockItem(
    val name: String,
    val price: Int,
)
