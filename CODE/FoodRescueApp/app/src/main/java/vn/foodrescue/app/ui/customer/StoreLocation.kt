package vn.foodrescue.app.ui.customer

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class StoreLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val isPreview: Boolean = false,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(address.isNotBlank())
    }
}

/** Maps chooses the origin and transport mode; opening this URL does not start navigation. */
internal fun directionsUrl(location: StoreLocation): String {
    val destination = "${location.latitude},${location.longitude}"
    return "https://www.google.com/maps/dir/?api=1&destination=" +
        URLEncoder.encode(destination, StandardCharsets.UTF_8.name())
}

internal fun mapOffers(boxes: List<DemoBox>) = boxes.filter { it.location != null }

internal fun selectedMapOffer(boxes: List<DemoBox>, selectedId: Int?): DemoBox? =
    mapOffers(boxes).let { located -> located.find { it.id == selectedId } ?: located.firstOrNull() }

// Fictional pickup points for reviewing the interface, not verified business addresses.
internal val previewLocations = mapOf(
    1 to StoreLocation(10.7694, 106.6989, "Đường Nguyễn Thái Bình, Quận 1", true),
    2 to StoreLocation(10.7777, 106.7030, "Đường Lý Tự Trọng, Quận 1", true),
    3 to StoreLocation(10.7885, 106.6968, "Đường Nguyễn Đình Chiểu, Quận 1", true),
    4 to StoreLocation(10.7828, 106.6870, "Đường Võ Văn Tần, Quận 3", true),
    5 to StoreLocation(10.8014, 106.7094, "Đường Xô Viết Nghệ Tĩnh, Bình Thạnh", true),
    6 to StoreLocation(10.7795, 106.6982, "Đường Pasteur, Quận 1", true),
    7 to StoreLocation(10.7712, 106.7055, "Đường Hồ Tùng Mậu, Quận 1", true),
)
