package com.foodrescue.app.feature.discovery.data

import com.foodrescue.app.core.common.PickupLocation
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.surprisebox.domain.BoxCategory
import com.foodrescue.app.feature.surprisebox.domain.CatalogBox

/** Local catalog for the customer frontend; no network or database access. */
internal class LocalCatalogRepository : CatalogRepository {
    override fun getBoxes(): List<CatalogBox> = boxes
}

private val catalogLocations = mapOf(
    1 to PickupLocation(10.7694, 106.6989, "Đường Nguyễn Thái Bình, Quận 1"),
    2 to PickupLocation(10.7777, 106.7030, "Đường Lý Tự Trọng, Quận 1"),
    3 to PickupLocation(10.7885, 106.6968, "Đường Nguyễn Đình Chiểu, Quận 1"),
    4 to PickupLocation(10.7828, 106.6870, "Đường Võ Văn Tần, Quận 3"),
    5 to PickupLocation(10.8014, 106.7094, "Đường Xô Viết Nghệ Tĩnh, Bình Thạnh"),
    6 to PickupLocation(10.7795, 106.6982, "Đường Pasteur, Quận 1"),
    7 to PickupLocation(10.7712, 106.7055, "Đường Hồ Tùng Mậu, Quận 1"),
)

private val boxes = listOf(
    CatalogBox(1, "Tiệm bánh Ban Mai", BoxCategory.BAKERY, "Surprise Box · Bánh & đồ ngọt",
        39000, 110000, 4, "17:00 – 19:00",
        "Một hộp thực phẩm thuộc nhóm bánh, được cửa hàng lựa chọn từ những sản phẩm còn tốt trong ngày. Các món cụ thể thay đổi theo lượng còn lại và được giữ bí mật đến khi nhận hộp.",
        "Có thể chứa lúa mì, trứng, sữa và các loại hạt. Liên hệ cửa hàng nếu bạn có dị ứng.", "Quận 1", 800, 17, "BM"),
    CatalogBox(2, "Bếp Nhà Xanh", BoxCategory.MEALS, "Surprise Box · Món ăn chế biến",
        49000, 130000, 3, "18:00 – 20:00",
        "Một hộp thực phẩm thuộc nhóm bữa ăn. Cửa hàng lựa chọn các món còn tốt trong ngày; hình ảnh giới thiệu không thể hiện thành phần của hộp bạn sẽ nhận.",
        "Có thể chứa đậu nành, trứng, cá hoặc hải sản. Cần kiểm tra thông tin an toàn trước khi đặt.", "Quận 1", 1200, 18, "NX"),
    CatalogBox(3, "Vườn Nhỏ", BoxCategory.PRODUCE, "Surprise Box · Rau củ & trái cây",
        35000, 90000, 6, "16:00 – 18:00",
        "Một hộp thực phẩm thuộc nhóm rau củ, trái cây. Sản phẩm cụ thể thay đổi theo ngày; hình dáng và độ chín có thể không đồng đều nhưng phải đáp ứng điều kiện sử dụng.",
        "Rửa sạch trước khi sử dụng. Liên hệ cửa hàng để kiểm tra thông tin dị ứng phù hợp với bạn.", "Quận 1", 1800, 16, "VN"),
    CatalogBox(4, "Lò bánh Tháng Năm", BoxCategory.BAKERY, "Surprise Box · Bánh & đồ ngọt",
        45000, 120000, 5, "18:00 – 20:00", "Hộp bánh bất ngờ từ các sản phẩm còn tốt trong ngày. Món cụ thể chỉ được bật mí khi nhận hộp.",
        "Có thể chứa lúa mì, trứng, sữa và các loại hạt.", "Quận 3", 2300, 18, "TN"),
    CatalogBox(5, "Bếp Mộc", BoxCategory.MEALS, "Surprise Box · Món ăn chế biến",
        65000, 160000, 2, "19:00 – 21:00", "Hộp thực phẩm thuộc nhóm bữa ăn, do cửa hàng lựa chọn. Không công bố trước danh sách món thực tế.",
        "Có thể chứa cá, đậu nành và hải sản.", "Bình Thạnh", 3200, 19, "M"),
    CatalogBox(6, "Quầy Nắng", BoxCategory.DRINKS, "Surprise Box · Đồ uống",
        29000, 80000, 4, "16:00 – 18:00",
        "Hộp thuộc nhóm đồ uống, do cửa hàng lựa chọn từ sản phẩm còn đủ điều kiện bán. Loại và số lượng đồ uống cụ thể là một bất ngờ; ảnh chỉ minh họa nhóm sản phẩm.",
        "Có thể chứa sữa hoặc các loại hạt. Xem hướng dẫn bảo quản từ cửa hàng khi nhận hộp.", "Quận 1", 1500, 16, "N"),
    CatalogBox(7, "Cửa hàng Góc Nhỏ", BoxCategory.MIXED, "Surprise Box · Thực phẩm tổng hợp",
        75000, 180000, 3, "17:00 – 19:00",
        "Hộp kết hợp nhiều nhóm thực phẩm; có thể gồm đồ ăn và đồ uống. Cửa hàng công bố thông tin an toàn, không công bố trước danh sách sản phẩm thực tế.",
        "Có thể chứa lúa mì, sữa, đậu nành, trứng và các loại hạt. Liên hệ cửa hàng nếu bạn có dị ứng.", "Quận 1", 2100, 17, "GN"),
).map { it.copy(location = catalogLocations[it.id]) }
