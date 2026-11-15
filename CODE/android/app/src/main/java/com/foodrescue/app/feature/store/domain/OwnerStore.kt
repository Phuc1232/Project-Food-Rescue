package com.foodrescue.app.feature.store.domain

import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.core.common.PickupLocation

internal enum class StoreReviewStatus { DRAFT, PENDING, APPROVED, CHANGES_REQUIRED }
internal enum class StoreField { NAME, CATEGORY, CONTACT, PHONE, ADDRESS, AREA, DESCRIPTION, OPEN, CLOSE, LOCATION, EMAIL }
internal enum class PayoutField { BANK, HOLDER, NUMBER }

internal data class OwnerStoreProfile(
    val name: String = "",
    val category: String = "Tiệm bánh",
    val contactName: String = "",
    val phone: String = "",
    val address: String = "",
    val area: String = "",
    val description: String = "",
    val opensAt: String = "07:00",
    val closesAt: String = "21:00",
    val location: GeoPoint? = null,
    val contactEmail: String = "",
) {
    val fullAddress: String get() = listOf(address, area, "TP. Hồ Chí Minh").filter { it.isNotBlank() }.joinToString(", ")
    val pickupLocation: PickupLocation? get() = location?.let { PickupLocation(it.latitude, it.longitude, fullAddress) }
    fun normalized() = copy(name = name.trim(), contactName = contactName.trim(), phone = phone.trim(),
        address = address.trim(), area = area.trim(), description = description.trim(),
        opensAt = opensAt.trim(), closesAt = closesAt.trim(), contactEmail = contactEmail.trim())
}

internal data class StorePayoutAccount(val bank: String = "", val holder: String = "", val number: String = "") {
    val maskedNumber: String get() = if (number.length > 4) "•••• " + number.takeLast(4) else "••••"
    fun normalized() = copy(bank = bank.trim(), holder = holder.trim().uppercase(java.util.Locale.ROOT), number = number.trim())
}

internal data class OwnerStore(
    val profile: OwnerStoreProfile = OwnerStoreProfile(),
    val status: StoreReviewStatus = StoreReviewStatus.DRAFT,
    val submittedAtMillis: Long? = null,
    val reviewNote: String = "",
    val payout: StorePayoutAccount? = null,
)

internal val storeCategories = listOf("Tiệm bánh", "Nhà hàng", "Quán ăn", "Cà phê & đồ uống", "Thực phẩm & tạp hóa")
