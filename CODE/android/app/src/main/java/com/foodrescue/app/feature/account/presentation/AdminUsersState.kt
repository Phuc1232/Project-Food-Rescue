package com.foodrescue.app.feature.account.presentation

import com.foodrescue.app.feature.account.domain.*
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class AdminUsersState(val loading: Boolean = true, val busy: Boolean = false,
    val accounts: List<ManagedAccount> = emptyList(), val selectedKey: String? = null,
    val query: String = "", val role: AccountRole? = null, val status: AccountStatus? = null,
    val draft: AccountDecision? = null, val confirming: Boolean = false, val attempted: Boolean = false,
    val error: String? = null, val notice: String? = null) {
    val selected: ManagedAccount? get() = accounts.find { it.key == selectedKey }
    val visible: List<ManagedAccount> get() {
        val term = searchText(query)
        return accounts.filter { (role == null || role == it.primaryRole) && (status == null || status == it.record.status) &&
            (term.isBlank() || searchText(listOfNotNull(it.name, it.email, it.phone, it.reference,
                it.relationships.joinToString(" ") { link -> link.storeName }).joinToString(" ")).contains(term)) }
    }
    val suspendedCount: Int get() = accounts.count { it.record.status == AccountStatus.SUSPENDED }
    fun countForRole(role: AccountRole): Int = accounts.count { it.primaryRole == role }
    val noteError: String? get() = if (attempted && draft?.let { !validAccountReason(it.reason) } == true)
        "Nhập lý do cụ thể từ 10 đến 500 ký tự." else null
}
// The management list uses the same precedence as account entry. This groups accounts;
// it does not remove their customer access or change any store membership permissions.
internal val ManagedAccount.primaryRole: AccountRole get() = when {
    AccountRole.ADMIN in roles -> AccountRole.ADMIN
    AccountRole.OWNER in roles -> AccountRole.OWNER
    AccountRole.STAFF in roles -> AccountRole.STAFF
    else -> AccountRole.CUSTOMER
}
internal val AccountRelationship.statusLabel: String get() = when (role) {
    AccountRole.OWNER -> "Trạng thái cửa hàng"
    AccountRole.STAFF -> "Quyền truy cập cửa hàng"
    AccountRole.CUSTOMER, AccountRole.ADMIN -> "Trạng thái liên kết"
}
private fun searchText(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd').trim()
internal val AccountRole.label: String get() = when (this) {
    AccountRole.CUSTOMER -> "Khách hàng"; AccountRole.OWNER -> "Chủ cửa hàng"
    AccountRole.STAFF -> "Nhân viên"; AccountRole.ADMIN -> "Quản trị viên"
}
internal val AccountStatus.label: String get() = if (this == AccountStatus.ACTIVE) "Đang hoạt động" else "Tạm khóa"
internal fun accountDate(value: Long?) = value?.let {
    SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(Date(it))
} ?: "Chưa ghi nhận"
internal val AccountStatus.action: String get() = if (this == AccountStatus.SUSPENDED) "Khóa tài khoản" else "Mở khóa tài khoản"

