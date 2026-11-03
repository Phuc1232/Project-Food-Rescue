package vn.foodrescue.app.ui.auth

// Vietnamese copy shared by fields, request errors and the preview controls.
internal fun AuthIssue.message(): String = when (this) {
    AuthIssue.NAME -> "Họ tên cần 2–120 ký tự."
    AuthIssue.EMAIL -> "Email chưa hợp lệ."
    AuthIssue.PASSWORD_REQUIRED -> "Nhập mật khẩu."
    AuthIssue.PASSWORD_POLICY -> "Mật khẩu cần từ 8 đến 128 ký tự."
    AuthIssue.CONFIRMATION -> "Mật khẩu chưa khớp."
    AuthIssue.CODE_FORMAT -> "Nhập đủ 6 chữ số của mã xác minh."
    AuthIssue.CODE_WRONG -> "Mã chưa đúng. Nhập lại mã."
    AuthIssue.CODE_EXPIRED -> "Mã hoặc phiên xác minh đã hết hạn. Vui lòng yêu cầu mã mới."
    AuthIssue.TOO_MANY_ATTEMPTS -> "Bạn đã thử quá nhiều lần. Hãy chờ 10 phút rồi yêu cầu mã mới hoặc thử lại."
    AuthIssue.COOLDOWN -> "Vui lòng chờ trước khi yêu cầu mã mới."
    AuthIssue.CREDENTIALS -> "Email hoặc mật khẩu chưa đúng."
    AuthIssue.NETWORK -> "Chưa thể kết nối. Kiểm tra mạng rồi thử lại."
    AuthIssue.EMAIL_USED -> "Không thể tạo tài khoản với email này. Hãy đăng nhập hoặc khôi phục mật khẩu."
    AuthIssue.MAIL_UNAVAILABLE -> "Chưa gửi được email xác minh. Vui lòng thử lại sau ít phút."
    AuthIssue.UNAVAILABLE -> "Chưa thể xử lý yêu cầu lúc này. Vui lòng thử lại sau."
}

internal val AuthScenario.label: String get() = when (this) {
    AuthScenario.SUCCESS -> "Thành công"
    AuthScenario.CREDENTIALS -> "Sai thông tin đăng nhập"
    AuthScenario.UNVERIFIED -> "Email chưa xác minh"
    AuthScenario.NETWORK -> "Mất kết nối"
    AuthScenario.EMAIL_USED -> "Email đã được sử dụng"
}

internal val termsDraft = listOf(
    "Về FoodRescue" to "FoodRescue kết nối người mua với cửa hàng có thực phẩm còn ngon chưa bán hết. Khách hàng đặt trước và đến cửa hàng nhận theo khung giờ được công bố.",
    "Thông tin của từng hộp" to "Trước khi đặt, hãy đọc mô tả, giá, giờ nhận và thông tin dị ứng nếu có. Thành phần hộp bất ngờ có thể thay đổi; ảnh giới thiệu không cam kết thành phần của từng hộp.",
    "Tài khoản và đơn hàng" to "Sử dụng thông tin liên hệ chính xác và giữ bí mật mật khẩu, mã xác minh. Kiểm tra điều kiện thanh toán, hủy và hoàn tiền của đơn trước khi xác nhận.",
    "Nội dung cần hoàn thiện" to "Nhóm cần bổ sung thông tin đơn vị vận hành, kênh hỗ trợ và các chính sách đã được duyệt trước khi phát hành ứng dụng. Đây là bản dự thảo để xem bố cục, chưa phải điều khoản chính thức.",
)
internal val privacyDraft = listOf(
    "Thông tin tài khoản" to "FoodRescue sử dụng họ tên, email và số điện thoại bạn cung cấp để quản lý tài khoản và liên hệ khi cần. Mật khẩu được lưu dưới dạng băm tại máy chủ; mã xác minh chỉ có hiệu lực trong thời gian giới hạn.",
    "Phiên đăng nhập" to "Ứng dụng lưu thông tin phiên đã mã hóa trên thiết bị để duy trì đăng nhập. Đăng xuất sẽ thu hồi phiên của thiết bị; đổi mật khẩu sẽ thu hồi các phiên hiện có.",
    "Quyền của bạn" to "Bạn có thể xem và cập nhật họ tên, số điện thoại trong phần Tài khoản. Nhóm đang hoàn thiện quy trình yêu cầu xóa dữ liệu và kênh liên hệ hỗ trợ trước khi phát hành.",
    "Nội dung cần hoàn thiện" to "Trước khi phát hành, nhóm cần chốt đơn vị xử lý dữ liệu, mục đích sử dụng, bên nhận dữ liệu, thời hạn lưu giữ và kênh tiếp nhận yêu cầu. Đây là bản dự thảo, chưa phải chính sách chính thức.",
)
