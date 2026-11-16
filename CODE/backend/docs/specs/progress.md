# Tiến độ backend FoodRescue (giai đoạn 3–9)

**Cập nhật:** 2026-11-15
**Nguồn chi tiết:** [roadmap-3-9.md](roadmap-3-9.md) (card từng task), thư mục `docs/specs/<task>/` (Gate A, Gate B, báo cáo).

Ký hiệu: ✅ xong (build xanh, đã commit) · 🔄 đang làm · ⬜ chưa làm · ⏸️ tạm dừng / cần người dùng quyết.

Từ T3.4 trở đi, người dùng cho phép agent tự quyết Gate A/Gate B trong phiên 2026-11-15 (ghi rõ quyết định trong từng
`01-business-definition.md` / `02-plan.md`). **Không push, không tạo PR**: các nhánh nối tiếp nhau (mỗi nhánh tách từ nhánh của
task trước) vì task trước chưa được merge vào `master`.

| Task | Nội dung | Trạng thái | Nhánh | Commit | Test (tổng / pass / skip / fail) | Spec |
|---|---|---|---|---|---|---|
| T0 | Baseline | ✅ | (đã merge, PR #11) | — | — | [t0-baseline](t0-baseline/) |
| T2.1 | Cập nhật hồ sơ cá nhân | ✅ | (đã merge, PR #10) | — | — | [t2-1-profile-update](t2-1-profile-update/) |
| T3.1 | Lưu trữ file | ✅ | (đã merge, PR #12) | — | 83 / 82 / 1 / 0 | [t3-1-file-storage](t3-1-file-storage/) |
| T3.2 | Đăng ký đối tác và xét duyệt | ✅ | (đã merge, PR #13) | — | 97 / 96 / 1 / 0 | [t3-2-partner-application](t3-2-partner-application/) |
| T3.3 | Thông tin cửa hàng, giờ, khóa/mở | ✅ | `feature/t3-3-store-profile` | c8f6955, a6a3d66 | 113 / 112 / 1 / 0 | [t3-3-store-profile](t3-3-store-profile/) |
| T3.4 | Quản lý nhân viên và phân quyền | ✅ | `feature/t3-4-staff-management` | 986d1d1, 6ebb8c7 | 122 / 121 / 1 / 0 | [t3-4-staff-management](t3-4-staff-management/) |
| T3.5 | Quản trị tài khoản người dùng | ✅ | `feature/t3-5-account-admin` | afff2a2, b2125e8 | 128 / 127 / 1 / 0 | [t3-5-account-admin](t3-5-account-admin/) |
| T4.1 | Menu nội bộ và lịch sử giá | ✅ | `feature/t4-1-menu` | 87265ba, 523d093 | 136 / 135 / 1 / 0 | [t4-1-menu](t4-1-menu/) |
| T4.2 | Mẫu Surprise Box | ✅ | `feature/t4-2-box-template` | 467a724, 7debb84 | 141 / 140 / 1 / 0 | [t4-2-box-template](t4-2-box-template/) |
| T4.3 | Đợt mở bán | ✅ | `feature/t4-3-sale-batch` | 2be0955, 3796e4f | 149 / 148 / 1 / 0 | [t4-3-sale-batch](t4-3-sale-batch/) |
| T4.4 | Đóng gói hộp thực tế | ✅ | `feature/t4-4-box-packing` | 4d4e892, e76dd84 | 158 / 157 / 1 / 0 | [t4-4-box-packing](t4-4-box-packing/) |
| T4.5 | Chính sách tài chính theo phiên bản | ✅ | `feature/t4-5-financial-policy` | 75705c6, 6d8170a | 164 / 163 / 1 / 0 | [t4-5-financial-policy](t4-5-financial-policy/) |
| T5.1 | Tìm kiếm Surprise Box và cửa hàng | ✅ | `feature/t5-1-search` | dcc2773, 2bfd3d6 | 170 / 169 / 1 / 0 | [t5-1-search](t5-1-search/) |
| T5.2 | Tạo đơn và giữ hộp | ✅ | `feature/t5-2-order-hold` | b38a791, 7e1c26c | 178 / 177 / 1 / 0 | [t5-2-order-hold](t5-2-order-hold/) |
| T5.3 | Hết hạn giữ hộp và theo dõi đơn | ✅ | `feature/t5-3-order-expiry` | 0ebd64b, ae38956 | 184 / 183 / 1 / 0 | [t5-3-order-expiry](t5-3-order-expiry/) |
| T6.1 | Port thanh toán và adapter payOS | ✅ | `feature/t6-1-payment-gateway` | | 192 / 191 / 1 / 0 | [t6-1-payment-gateway](t6-1-payment-gateway/) |
| T6.2 | Tạo yêu cầu thanh toán | 🔄 |  | |  |  |
| T6.3 | Webhook xác nhận thanh toán | ⬜ | | | | |
| T6.4 | Tiền đến muộn, sai số tiền, đối chiếu | ⬜ | | | | |
| T6.5 | Hoàn tiền có kiểm soát | ⬜ | | | | |
| T7.1 | Mã nhận hàng | ⬜ | | | | |
| T7.2 | Phiên bàn giao và sự cố | ⬜ | | | | |
| T7.3 | Hủy đơn | ⬜ | | | | |
| T7.4 | No-show và kiểm tra lại hộp | ⬜ | | | | |
| T7.5 | Hộp không đạt, ngừng bán, khóa cửa hàng có đơn tồn | ⬜ | | | | |
| T8.1 | Khiếu nại | ⬜ | | | | |
| T8.2 | Thông báo | ⬜ | | | | |
| T8.3 | Hoa hồng, quyền lợi cửa hàng, doanh thu | ⬜ | | | | |
| T8.4 | Đối soát và chi trả | ⬜ | | | | |
| T8.5 | Giám sát và thống kê quản trị | ⬜ | | | | |
| T9.1 | Test xuyên suốt và tải đồng thời | ⬜ | | | | |
| T9.2 | Giám sát | ⬜ | | | | |
| T9.3 | Rà soát bảo mật | ⬜ | | | | |
| T9.4 | Backup và phục hồi | ⬜ | | | | |
| T9.5 | CI/CD và staging | ⬜ | | | | |

## Quyết định agent tự chốt (tóm tắt, chi tiết trong spec từng task)

- T3.3: duyệt nguyên Q1–Q13 (người dùng "ok").
- T3.4: D01 = lời mời (tài khoản có sẵn) + link SET_PASSWORD 72h (tài khoản mới); 6 mã quyền staff; sửa nhỏ auth.
- T3.5: Khóa = LOCKED (có mở khóa), thu hồi mọi phiên; còn đơn tồn đọng thì bắt buộc outstandingPlan (409 OUTSTANDING_WORK).
- T4.1: 9 nhóm thực phẩm seed; giá nguyên đồng 1..100tr; tài liệu giá có phiên bản (bảng mới); đọc menu: owner/staff MENU_VIEW/admin.
- T4.2: Không xóa mẫu (chỉ ngừng); giá trị tối thiểu mặc định ≥ giá bán; ảnh bìa cho khách để T5.1.
- T4.3: saleDate suy ra từ giờ nhận; khung nhận trong giờ mở cửa ≤ 24h; sửa chỉ khi DRAFT/PAUSED; admin BLOCKED vĩnh viễn; BatchAvailability là định nghĩa tồn khả dụng.
- T4.4: Đóng hộp 1 lệnh với requestKey; không đạt giá trị/an toàn → 400 không tạo; kiểm tra thực phẩm không đạt → hộp BLOCKED; giá đổi → 409 PRICE_CHANGED.
- T4.5: Seed hoa hồng 10%, giữ lại no-show 50% (giả định, tài liệu không nêu số); hiệu lực = effective_from mới nhất ≤ t; giờ phản ánh cố định 48.
- T5.1: Khu vực = vị trí + bán kính hoặc khung bản đồ; chi tiết đợt PAUSED/ENDED vẫn xem được (orderable=false); ảnh bìa cho khách qua /surprise-boxes/{id}/cover.
- T5.2: Giữ hộp: khóa account → store/batch FOR SHARE → hộp FOR UPDATE SKIP LOCKED; tối đa 5 hộp/đơn; chủ cửa hàng không tự đặt; acceptedPolicyVersion bắt buộc.
- T5.3: Job hết hạn 30 s (FOR UPDATE SKIP LOCKED); hủy đơn đã thanh toán để T7.3; cửa hàng thấy tên hiển thị khách, không email; V14 thay trigger để thêm status_reason.
- T6.1: payment.gateway=fake mặc định (dev/test), prod bắt buộc payos + 3 khóa; expiredAt gửi epoch giây; chữ ký webhook khớp vector tài liệu payOS.

## Việc cần người dùng làm / xem lại

- Push các nhánh và mở PR theo thứ tự (T3.3 → T3.4 → ...), hoặc cho phép agent làm.
