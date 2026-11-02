package vn.foodrescue.app.data.model

import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

enum class FoodCategory(val label: String) { ALL("Tất cả"), BAKERY("Bánh & cà phê"), MEAL("Bữa ăn"), GROCERY("Thực phẩm") }

data class SurpriseBox(
    val id: String,
    val storeName: String,
    val name: String,
    val category: FoodCategory,
    val price: Long,
    val referenceValue: Long,
    val pickup: String,
    val distanceKm: Double,
    val remaining: Int,
    val address: String,
    val description: String,
    val allergens: String,
    val artwork: Int,
)

fun Long.vnd(): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(this) + " đ"

private fun String.searchKey() = Normalizer.normalize(lowercase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

fun List<SurpriseBox>.filterBoxes(query: String, category: FoodCategory): List<SurpriseBox> {
    val key = query.trim().searchKey()
    return filter { box ->
        (category == FoodCategory.ALL || box.category == category) &&
            (key.isEmpty() || "${box.storeName} ${box.name}".searchKey().contains(key))
    }
}
