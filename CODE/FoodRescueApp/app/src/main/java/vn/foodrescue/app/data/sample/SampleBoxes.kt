package vn.foodrescue.app.data.sample

import vn.foodrescue.app.data.model.FoodCategory
import vn.foodrescue.app.data.model.SurpriseBox

/** Fictional preview data. Never use these IDs or prices to create an order. */
object SampleBoxes {
    val items = listOf(
        SurpriseBox("demo-bakery", "Tiệm Bánh Mộc", "Hộp bánh cuối ngày", FoodCategory.BAKERY,
            39000, 120000, "Hôm nay · 18:00–19:30", .8, 3,
            "12 Đường số 3, TP. Hồ Chí Minh · địa chỉ minh họa",
            "Một chút bất ngờ từ quầy bánh: có thể là bánh mì, bánh ngọt hoặc bánh sừng bò còn ngon trong ngày. Thành phần thực tế tùy lượng bánh còn lại.",
            "Có thể chứa lúa mì, trứng, sữa và các loại hạt. Liên hệ cửa hàng nếu bạn có dị ứng.", 0),
        SurpriseBox("demo-meal", "Bếp Nhà Xanh", "Bữa ngon bất ngờ", FoodCategory.MEAL,
            45000, 100000, "Hôm nay · 19:00–20:00", 1.2, 2,
            "28 Đường số 5, TP. Hồ Chí Minh · địa chỉ minh họa",
            "Một phần ăn được chuẩn bị trong ngày, kết hợp món chính và món ăn kèm tùy thực phẩm còn lại. Bạn sẽ biết món cụ thể khi nhận hộp.",
            "Có thể chứa đậu nành, trứng, hải sản hoặc đậu phộng. Hỏi cửa hàng trước khi đặt nếu có dị ứng.", 1),
        SurpriseBox("demo-grocery", "Góc Vườn Nhỏ", "Hộp rau củ theo ngày", FoodCategory.GROCERY,
            29000, 75000, "Hôm nay · 17:30–19:00", 1.7, 5,
            "46 Đường số 2, TP. Hồ Chí Minh · địa chỉ minh họa",
            "Rau củ và trái cây còn sử dụng tốt, được lựa chọn theo lượng hàng trong ngày. Hình dáng và chủng loại có thể thay đổi.",
            "Thành phần thay đổi theo ngày. Vui lòng hỏi cửa hàng về loại thực phẩm bạn cần tránh.", 2),
    )
}
