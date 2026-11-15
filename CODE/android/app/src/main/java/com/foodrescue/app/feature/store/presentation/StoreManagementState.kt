package com.foodrescue.app.feature.store.presentation

import com.foodrescue.app.feature.store.domain.*

internal enum class StoreSaved { DRAFT, APPLICATION, PROFILE, PAYOUT }
internal data class StoreManagementState(
    val store: OwnerStore? = null,
    val draft: OwnerStoreProfile = OwnerStoreProfile(),
    val payoutDraft: StorePayoutAccount = StorePayoutAccount(),
    val step: Int = 0,
    val errors: Set<StoreField> = emptySet(),
    val payoutErrors: Set<PayoutField> = emptySet(),
    val accepted: Boolean = false,
    val acceptanceError: Boolean = false,
    val busy: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val saved: StoreSaved? = null,
    val banks: List<PayoutBank> = emptyList(),
)

internal val StoreField.label: String get() = when (this) {
    StoreField.NAME -> "Tên cửa hàng"
    StoreField.CATEGORY -> "Loại hình kinh doanh"
    StoreField.CONTACT -> "Người đại diện"
    StoreField.PHONE -> "Số điện thoại"
    StoreField.ADDRESS -> "Số nhà, tên đường"
    StoreField.AREA -> "Phường / xã"
    StoreField.DESCRIPTION -> "Giới thiệu cửa hàng"
    StoreField.OPEN -> "Giờ mở cửa"
    StoreField.CLOSE -> "Giờ đóng cửa"
    StoreField.LOCATION -> "Vị trí trên bản đồ"
    StoreField.EMAIL -> "Email liên hệ cửa hàng (không bắt buộc)"
}

internal val StoreField.errorText: String get() = when (this) {
    StoreField.NAME -> "Nhập tên cửa hàng từ 2 đến 100 ký tự."
    StoreField.CATEGORY -> "Chọn loại hình kinh doanh."
    StoreField.CONTACT -> "Nhập tên người đại diện từ 2 đến 100 ký tự."
    StoreField.PHONE -> "Nhập số điện thoại 10 số bắt đầu bằng 0 hoặc dạng +84."
    StoreField.ADDRESS -> "Nhập địa chỉ từ 5 đến 180 ký tự."
    StoreField.AREA -> "Nhập phường / xã từ 2 đến 100 ký tự."
    StoreField.DESCRIPTION -> "Giới thiệu tối đa 500 ký tự."
    StoreField.OPEN -> "Nhập giờ theo dạng HH:mm, ví dụ 07:00."
    StoreField.CLOSE -> "Nhập giờ hợp lệ và khác giờ mở cửa."
    StoreField.LOCATION -> "Chọn vị trí cửa hàng trên bản đồ."
    StoreField.EMAIL -> "Nhập địa chỉ email liên hệ hợp lệ."
}
