package vn.foodrescue.app.ui.auth

// Vietnamese copy shared by fields, request errors and the preview controls.
internal fun AuthIssue.message(): String = when (this) {
    AuthIssue.NAME -> "Nhập họ và tên, tối đa 120 ký tự."
    AuthIssue.EMAIL -> "Nhập địa chỉ email hợp lệ, tối đa 254 ký tự."
    AuthIssue.PASSWORD_REQUIRED -> "Vui lòng nhập mật khẩu."
    AuthIssue.PASSWORD_POLICY -> "Dùng từ 15 đến 128 ký tự; có thể chọn một cụm từ dễ nhớ."
    AuthIssue.CONFIRMATION -> "Mật khẩu nhập lại chưa khớp."
    AuthIssue.CODE_FORMAT -> "Nhập đủ 6 chữ số của mã xác minh."
    AuthIssue.CODE_WRONG -> "Mã chưa đúng. Kiểm tra và nhập lại nhé."
    AuthIssue.CODE_EXPIRED -> "Mã hoặc phiên xác minh đã hết hạn. Vui lòng yêu cầu mã mới."
    AuthIssue.TOO_MANY_ATTEMPTS -> "Bạn đã nhập sai nhiều lần. Hãy yêu cầu mã mới."
    AuthIssue.COOLDOWN -> "Vui lòng chờ trước khi yêu cầu mã mới."
    AuthIssue.CREDENTIALS -> "Email hoặc mật khẩu chưa đúng. Vui lòng thử lại."
    AuthIssue.NETWORK -> "Chưa thể kết nối. Kiểm tra mạng rồi thử lại."
    AuthIssue.EMAIL_USED -> "Không thể tạo tài khoản với email này. Hãy đăng nhập hoặc khôi phục mật khẩu."
    AuthIssue.UNAVAILABLE -> "Chức năng tài khoản chưa kết nối với máy chủ. Vui lòng thử lại sau."
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
    "Thông tin tài khoản" to "Luồng tài khoản dự kiến sử dụng họ tên và email để nhận diện, liên hệ và khôi phục quyền truy cập. Mật khẩu cần được xử lý an toàn tại máy chủ khi tích hợp xác thực.",
    "Trong bản xem thử này" to "Thông tin nhập trong các màn tài khoản chỉ được giữ tạm trong bộ nhớ ứng dụng. Bản xem thử không gửi email, không tạo tài khoản và không lưu mật khẩu vào cơ sở dữ liệu.",
    "Quyền của bạn" to "Bản phát hành cần hướng dẫn cách xem, sửa thông tin, yêu cầu xóa tài khoản và liên hệ bộ phận hỗ trợ. Các thao tác này chưa được cung cấp bởi luồng xem thử.",
    "Nội dung cần hoàn thiện" to "Trước khi phát hành, nhóm cần chốt đơn vị xử lý dữ liệu, mục đích sử dụng, bên nhận dữ liệu, thời hạn lưu giữ và kênh tiếp nhận yêu cầu. Đây là bản dự thảo, chưa phải chính sách chính thức.",
)
