package com.foodrescue.app.feature.store.presentation

import com.foodrescue.app.feature.store.domain.*
import java.text.Normalizer
import java.util.Locale

internal enum class SupervisionFilter(val label: String) { ALL("Tất cả"), ACTIVE("Không hạn chế"), RESTRICTED("Có hạn chế") }
internal enum class SupervisionSection(val label: String) { OVERVIEW("Tổng quan"), MENU("Menu"), BATCHES("Đợt bán"), BOXES("Hộp thực tế"), HISTORY("Nhật ký") }

internal data class StoreSupervisionState(val stores: List<SupervisedStore> = emptyList(),
    val detail: StoreSupervisionDetail? = null, val loading: Boolean = true, val busy: Boolean = false,
    val query: String = "", val filter: SupervisionFilter = SupervisionFilter.ALL,
    val selectedKey: String? = null, val section: SupervisionSection = SupervisionSection.OVERVIEW,
    val boxId: String? = null, val menuId: String? = null, val draft: StoreRestrictionDraft? = null,
    val attempted: Boolean = false, val confirming: Boolean = false, val error: String? = null,
    val notice: String? = null) {
    val restrictedCount get() = stores.count { it.store.governance.let { g -> g.suspension != null || g.blockedBatches.isNotEmpty() } }
    val visible get() = stores.filter { entry ->
        val restricted = entry.store.governance.let { it.suspension != null || it.blockedBatches.isNotEmpty() }
        (filter == SupervisionFilter.ALL || (filter == SupervisionFilter.RESTRICTED) == restricted) &&
            listOf(entry.reference, entry.store.profile.name, entry.store.profile.area, entry.store.profile.contactEmail,
                entry.store.profile.phone).any { normalize(query) in normalize(it) }
    }
    val noteError get() = if (attempted && draft?.let { !validRestrictionNote(it.note) } == true)
        "Nhập lý do cụ thể từ 10 đến 500 ký tự." else null
}

private fun normalize(value: String) = Normalizer.normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd')

internal val StoreRestrictionAction.label: String get() = when (this) {
    StoreRestrictionAction.SUSPEND_STORE -> "Tạm ngừng bán cửa hàng"
    StoreRestrictionAction.RESTORE_STORE -> "Khôi phục quyền bán"
    StoreRestrictionAction.STOP_BATCH -> "Ngừng bán đợt này"
    StoreRestrictionAction.RELEASE_BATCH -> "Gỡ hạn chế đợt bán"
}

internal val StoreRestrictionAction.impact: String get() = when (this) {
    StoreRestrictionAction.SUSPEND_STORE -> "Ẩn cửa hàng khỏi bản đồ và danh sách khám phá; chặn đặt hộp mới ở mọi đợt bán."
    StoreRestrictionAction.STOP_BATCH -> "Ẩn đợt bán này khỏi danh sách khách hàng và chặn đặt hộp mới. Các đợt bán khác vẫn hoạt động."
    StoreRestrictionAction.RESTORE_STORE -> "Cho phép cửa hàng hiển thị trở lại. Đợt bán bị hạn chế riêng vẫn bị chặn. Chỉ đợt đang mở và còn trong giờ bán mới xuất hiện."
    StoreRestrictionAction.RELEASE_BATCH -> "Gỡ hạn chế quản trị của đợt bán. Đợt đã kết thúc hoặc do chủ cửa hàng tạm dừng sẽ không tự mở lại. Cửa hàng bị tạm ngừng vẫn chưa được bán."
}
