package vn.foodrescue.app.ui.customer

import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

enum class BoxCategory(val label: String) {
    BAKERY("Bánh & đồ ngọt"),
    MEALS("Món ăn chế biến"),
    PRODUCE("Rau củ & trái cây"),
    DRINKS("Đồ uống"),
    MIXED("Thực phẩm tổng hợp"),
}

/** Public catalog metadata only. Actual contents of each Surprise Box are never searchable. */
data class DemoBox(
    val id: Int, val store: String, val category: BoxCategory, val name: String,
    val price: Int, val minimumValue: Int, val remaining: Int, val pickup: String,
    val description: String, val allergens: String, val district: String,
    val distanceMeters: Int, val pickupStartHour: Int, val initials: String,
    val location: StoreLocation? = null,
)

// Fictional stores and distances for the UI preview; not live inventory.
val demoBoxes = listOf(
    DemoBox(1, "Tiệm bánh Ban Mai", BoxCategory.BAKERY, "Surprise Box · Bánh & đồ ngọt",
        39000, 110000, 4, "17:00 – 19:00",
        "Một hộp thực phẩm thuộc nhóm bánh, được cửa hàng lựa chọn từ những sản phẩm còn tốt trong ngày. Các món cụ thể thay đổi theo lượng còn lại và được giữ bí mật đến khi nhận hộp.",
        "Có thể chứa lúa mì, trứng, sữa và các loại hạt. Liên hệ cửa hàng nếu bạn có dị ứng.", "Quận 1", 800, 17, "BM"),
    DemoBox(2, "Bếp Nhà Xanh", BoxCategory.MEALS, "Surprise Box · Món ăn chế biến",
        49000, 130000, 3, "18:00 – 20:00",
        "Một hộp thực phẩm thuộc nhóm bữa ăn. Cửa hàng lựa chọn các món còn tốt trong ngày; hình ảnh giới thiệu không thể hiện thành phần của hộp bạn sẽ nhận.",
        "Có thể chứa đậu nành, trứng, cá hoặc hải sản. Cần kiểm tra thông tin an toàn trước khi đặt.", "Quận 1", 1200, 18, "NX"),
    DemoBox(3, "Vườn Nhỏ", BoxCategory.PRODUCE, "Surprise Box · Rau củ & trái cây",
        35000, 90000, 6, "16:00 – 18:00",
        "Một hộp thực phẩm thuộc nhóm rau củ, trái cây. Sản phẩm cụ thể thay đổi theo ngày; hình dáng và độ chín có thể không đồng đều nhưng phải đáp ứng điều kiện sử dụng.",
        "Rửa sạch trước khi sử dụng. Liên hệ cửa hàng để kiểm tra thông tin dị ứng phù hợp với bạn.", "Quận 1", 1800, 16, "VN"),
    DemoBox(4, "Lò bánh Tháng Năm", BoxCategory.BAKERY, "Surprise Box · Bánh & đồ ngọt",
        45000, 120000, 5, "18:00 – 20:00", "Hộp bánh bất ngờ từ các sản phẩm còn tốt trong ngày. Món cụ thể chỉ được bật mí khi nhận hộp.",
        "Có thể chứa lúa mì, trứng, sữa và các loại hạt.", "Quận 3", 2300, 18, "TN"),
    DemoBox(5, "Bếp Mộc", BoxCategory.MEALS, "Surprise Box · Món ăn chế biến",
        65000, 160000, 2, "19:00 – 21:00", "Hộp thực phẩm thuộc nhóm bữa ăn, do cửa hàng lựa chọn. Không công bố trước danh sách món thực tế.",
        "Có thể chứa cá, đậu nành và hải sản.", "Bình Thạnh", 3200, 19, "M"),
    DemoBox(6, "Quầy Nắng", BoxCategory.DRINKS, "Surprise Box · Đồ uống",
        29000, 80000, 4, "16:00 – 18:00",
        "Hộp thuộc nhóm đồ uống, do cửa hàng lựa chọn từ sản phẩm còn đủ điều kiện bán. Loại và số lượng đồ uống cụ thể là một bất ngờ; ảnh chỉ minh họa nhóm sản phẩm.",
        "Có thể chứa sữa hoặc các loại hạt. Xem hướng dẫn bảo quản từ cửa hàng khi nhận hộp.", "Quận 1", 1500, 16, "N"),
    DemoBox(7, "Cửa hàng Góc Nhỏ", BoxCategory.MIXED, "Surprise Box · Thực phẩm tổng hợp",
        75000, 180000, 3, "17:00 – 19:00",
        "Hộp kết hợp nhiều nhóm thực phẩm; có thể gồm đồ ăn và đồ uống. Cửa hàng công bố thông tin an toàn, không công bố trước danh sách sản phẩm thực tế.",
        "Có thể chứa lúa mì, sữa, đậu nành, trứng và các loại hạt. Liên hệ cửa hàng nếu bạn có dị ứng.", "Quận 1", 2100, 17, "GN"),
).map { it.copy(location = previewLocations[it.id]) }

/** Prices are whole VND. Null in DiscoveryFilters means no price restriction. */
data class PriceRange(val min: Int, val max: Int) {
    init { require(min >= 0 && max >= min) }
    operator fun contains(price: Int) = price in min..max
}
const val PRICE_STEP = 1_000
fun catalogPriceCeiling(catalog: List<DemoBox> = demoBoxes): Int {
    val highest = (catalog.maxOfOrNull { it.price } ?: 0).coerceAtLeast(100_000)
    return (((highest.toLong() + 49_999) / 50_000) * 50_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
fun selectedPriceRange(start: Float, end: Float, ceiling: Int): PriceRange? {
    fun snap(value: Float) = ((value.coerceIn(0f, ceiling.toFloat()) / PRICE_STEP).roundToInt().toLong() * PRICE_STEP)
        .coerceIn(0, ceiling.toLong()).toInt()
    val first = snap(start)
    val second = snap(end)
    val low = minOf(first, second)
    val high = maxOf(first, second)
    return if (low == 0 && high == ceiling) null else PriceRange(low, high)
}
enum class PickupBand(val label: String) { ALL("Mọi khung giờ"), BEFORE_18("Bắt đầu trước 18:00"), FROM_18("Bắt đầu từ 18:00") }
enum class CatalogSort(val label: String) { NEAREST("Gần nhất"), PRICE("Giá thấp nhất") }
data class DiscoveryFilters(val category: BoxCategory? = null, val price: PriceRange? = null,
    val pickup: PickupBand = PickupBand.ALL, val sort: CatalogSort = CatalogSort.NEAREST) {
    val activeCount: Int get() = (if (category != null) 1 else 0) + (if (price != null) 1 else 0) + (if (pickup != PickupBand.ALL) 1 else 0) + (if (sort != CatalogSort.NEAREST) 1 else 0)
}
fun money(value: Int): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ"
fun distance(meters: Int): String = String.format(Locale.forLanguageTag("vi-VN"), "%.1f km", meters / 1000.0)
private fun String.searchKey() = Normalizer.normalize(lowercase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
fun filterCatalog(query: String, district: String, filters: DiscoveryFilters, catalog: List<DemoBox> = demoBoxes): List<DemoBox> {
    val result = catalog.filter {
        (district == "TP. Hồ Chí Minh" || it.district == district) &&
        (filters.category == null || it.category == filters.category) &&
        (it.name + " " + it.store + " " + it.category.label).searchKey().contains(query.trim().searchKey()) &&
        (filters.price == null || it.price in filters.price) && when (filters.pickup) {
            PickupBand.ALL -> true
            PickupBand.BEFORE_18 -> it.pickupStartHour < 18
            PickupBand.FROM_18 -> it.pickupStartHour >= 18
        }
    }
    return if (filters.sort == CatalogSort.PRICE) result.sortedBy { it.price } else result.sortedBy { it.distanceMeters }
}
