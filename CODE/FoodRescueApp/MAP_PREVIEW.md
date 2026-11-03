# Bản đồ cửa hàng

Mở bản xem thử → **Khám phá / Tìm kiếm → biểu tượng bản đồ**.

- Bản đồ thật dùng MapLibre Android 13.7.1 và OpenFreeMap; không cần API key trong bản này. Cần Internet để tải nền bản đồ.
- Bản đồ và danh sách dùng chung `DiscoveryState`: từ khóa, khu vực, loại hộp, khoảng giá, giờ nhận và cách sắp xếp.
- Chạm ghim hoặc dùng mũi tên trên thẻ để chọn cửa hàng; **Xem hộp** mở chi tiết, Back quay lại bản đồ.
- Các ghim gần nhau ưu tiên hiện nhãn vừa đủ chỗ. Chấm vị trí vẫn có thể chọn; nút la bàn đưa tất cả địa điểm vào khung nhìn.
- **Khu vực nhận hộp → Dùng vị trí hiện tại** xin quyền vị trí khi dùng ứng dụng, chấp nhận cả vị trí gần đúng. Có thông báo khi từ chối quyền, GPS tắt hoặc hết thời gian lấy vị trí. Không xin vị trí nền và không lưu tọa độ người dùng vào đĩa.
- Khoảng cách đường chim bay được tính từ tọa độ thiết bị đến điểm cửa hàng mẫu, không phải quãng đường chạy xe. Chưa có quyền/vị trí thì không hiển thị số khoảng cách giả. Bản đồ có chấm vị trí và nút đưa về vị trí của tôi.
- Chọn khu vực thủ công bằng các tên quận, huyện quen thuộc trước đây; đây là vùng tìm kiếm của bản thử, không phải danh sách đơn vị hành chính hiện hành. Danh sách và bản đồ dùng chung khu vực và bộ lọc.
- **Chỉ đường** mở Google Maps; nếu không có ứng dụng thì mở URL trong trình duyệt. FoodRescue không truyền vị trí xuất phát và không tự bắt đầu dẫn đường.
- Cửa hàng, tồn kho và tọa độ hiện là dữ liệu mẫu. Có xác nhận trước khi mở chỉ đường tới điểm mẫu; đây chưa phải danh sách cửa hàng đã xác minh.

Khi nối API: cung cấp `StoreLocation` với tọa độ/địa chỉ đã xác minh và `isPreview = false`. Hộp chưa có tọa độ vẫn có trong danh sách nhưng không được gắn vào vị trí giả trên bản đồ. Khi chuyển sang dữ liệu nhiều hộp mỗi cửa hàng, cần nhóm theo ID cửa hàng trước khi hiển thị.

Nguồn triển khai: [MapLibre Quickstart](https://maplibre.org/maplibre-native/android/examples/getting-started/), [OpenFreeMap](https://openfreemap.org/quick_start/), [Google Maps URLs](https://developers.google.com/maps/documentation/urls/guide). Giấy phép và ghi công: `ASSET_CREDITS.md`.

Quyền vị trí: [Android Developers](https://developer.android.com/develop/sensors-and-location/location/permissions). Tên đơn vị hành chính khi tích hợp API cần đối chiếu [danh sách chính thức của TP.HCM](https://tphcm.baochinhphu.vn/danh-sach-chinh-thuc-168-phuong-xa-cua-tphcm-sau-sap-xep-101250617000144317.htm).
