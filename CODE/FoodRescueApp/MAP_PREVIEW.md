# Bản đồ cửa hàng

Mở bản xem thử → **Khám phá / Tìm kiếm → biểu tượng bản đồ**.

- Bản đồ thật dùng MapLibre Android 13.7.1 và OpenFreeMap; không cần API key trong bản này. Cần Internet để tải nền bản đồ.
- Bản đồ và danh sách dùng chung `DiscoveryState`: từ khóa, khu vực, loại hộp, khoảng giá, giờ nhận và cách sắp xếp.
- Chạm ghim hoặc dùng mũi tên trên thẻ để chọn cửa hàng; **Xem hộp** mở chi tiết, Back quay lại bản đồ.
- Các ghim gần nhau ưu tiên hiện nhãn vừa đủ chỗ. Chấm vị trí vẫn có thể chọn; nút la bàn đưa tất cả địa điểm vào khung nhìn.
- **Chỉ đường** mở Google Maps; nếu không có ứng dụng thì mở URL trong trình duyệt. FoodRescue không xin GPS, không truyền vị trí xuất phát và không tự bắt đầu dẫn đường.
- Cửa hàng, tồn kho và tọa độ hiện là dữ liệu mẫu. Có xác nhận trước khi mở chỉ đường tới điểm mẫu; đây chưa phải danh sách cửa hàng đã xác minh.

Khi nối API: cung cấp `StoreLocation` với tọa độ/địa chỉ đã xác minh và `isPreview = false`. Hộp chưa có tọa độ vẫn có trong danh sách nhưng không được gắn vào vị trí giả trên bản đồ. Khi chuyển sang dữ liệu nhiều hộp mỗi cửa hàng, cần nhóm theo ID cửa hàng trước khi hiển thị.

Nguồn triển khai: [MapLibre Quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/), [OpenFreeMap](https://openfreemap.org/quick_start/), [Google Maps URLs](https://developers.google.com/maps/documentation/urls/guide). Giấy phép và ghi công: `ASSET_CREDITS.md`.
