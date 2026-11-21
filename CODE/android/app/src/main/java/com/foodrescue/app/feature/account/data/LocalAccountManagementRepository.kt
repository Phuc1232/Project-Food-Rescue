package com.foodrescue.app.feature.account.data

import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.account.domain.*
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.staff.data.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only projection of existing identities; contact email never becomes a login identity. */
internal class LocalAccountManagementRepository(context: Context, email: String,
    private val legacy: LocalLegacyAccountHistory = LocalLegacyAccountHistory(context),
    private val clock: () -> Long = System::currentTimeMillis) : AccountManagementRepository {
    private val actor = accountEmail(email)
    private val records = LocalAccountStore(context)
    private val employees = LocalEmployeeDirectory(context)
    private val directory = LocalPublicStoreRepository(context)
    private val stores = LocalStoreRecords(context)
    private val ledger = LocalOrderLedger(context)

    override suspend fun accounts(): List<ManagedAccount> = withContext(Dispatchers.IO) {
        requireAdmin()
        employees.withRecords { members -> synchronized(LocalCommerceLock) { project(members) } }
    }
    override suspend fun decide(key: String, decision: AccountDecision): ManagedAccount = withContext(Dispatchers.IO) {
        requireAdmin()
        // Membership -> commerce is the same order used by staff operations.
        employees.withRecords { members -> synchronized(LocalCommerceLock) {
            val user = project(members).find { it.key == key } ?: throw StaleAccountDecision()
            val updated = records.change(key) { it.changeStatus(decision, actor, clock(),
                user.protected, key == LocalAccountStore.key(actor)) }
            user.copy(record = updated)
        } }
    }
    private fun requireAdmin() { check(AdminIdentity.matches(actor)) }

    private fun project(members: List<EmployeeRecord>): List<ManagedAccount> {
        val saved = records.all().associateBy { it.key }
        val registered = directory.registeredAccounts().mapNotNull { (key, id) -> stores.read(key)?.let { Triple(key, id, it) } }
        val orders = (ledger.read() + legacy.rows()).distinctBy { it.account to it.order.id }
        val knownEmails = members.flatMap { listOf(it.ownerEmail, it.employee.email) }.associateBy(LocalAccountStore::key) +
            mapOf(LocalAccountStore.key(actor) to actor)
        val keys = saved.keys + registered.map { it.first } + knownEmails.keys + orders.map { it.account }
        return keys.filter { it.matches(Regex("[0-9a-f]{64}")) }.map { key ->
            val record = saved[key] ?: AccountRecord(key)
            val owner = registered.find { it.first == key }
            val staff = members.find { LocalAccountStore.key(it.employee.email) == key }
            val address = record.profile?.email ?: knownEmails[key]
            val isAdmin = address?.let(AdminIdentity::matches) == true
            val customerOrders = orders.filter { it.account == key }
            val roles = buildSet {
                if (isAdmin) add(AccountRole.ADMIN) else {
                    add(AccountRole.CUSTOMER)
                    if (owner != null) add(AccountRole.OWNER)
                    if (staff != null) add(AccountRole.STAFF)
                }
            }
            val links = buildList {
                owner?.let { (_, id, store) -> add(AccountRelationship(AccountRole.OWNER, id,
                    store.profile.name.ifBlank { "Cửa hàng chưa đặt tên" }, when (store.status) {
                        StoreReviewStatus.DRAFT -> "Hồ sơ nháp"
                        StoreReviewStatus.PENDING -> "Chờ xét duyệt"
                        StoreReviewStatus.CHANGES_REQUIRED -> "Cần bổ sung hồ sơ"
                        StoreReviewStatus.APPROVED -> if (store.governance.suspension == null) "Đã duyệt" else "Tạm ngừng bán"
                    })) }
                staff?.let { member -> add(AccountRelationship(AccountRole.STAFF, member.employee.storeId,
                    registered.find { it.second == member.employee.storeId }?.third?.profile?.name ?: "Cửa hàng CH-${kotlin.math.abs(member.employee.storeId.toLong())}",
                    if (member.employee.status == EmployeeStatus.ACTIVE) "Đang được cấp quyền" else "Đã thu hồi quyền nhân viên",
                    member.employee.effectivePermissions.sortedBy { it.name }.map { when (it) {
                        EmployeePermission.VIEW_ORDERS -> "Xem đơn hàng"
                        EmployeePermission.PREPARE_BOXES -> "Đóng gói hộp"
                        EmployeePermission.HAND_OVER -> "Bàn giao hộp"
                        EmployeePermission.OPERATE_SALES -> "Vận hành đợt bán"
                    } })) }
            }
            val name = record.profile?.name ?: staff?.employee?.name ?: owner?.third?.profile?.contactName?.takeIf(String::isNotBlank)
                ?: if (isAdmin) "Quản trị viên" else "Tài khoản ${key.take(8).uppercase()}"
            val storeIds = links.map { it.storeId }.toSet()
            val relevantOrders = orders.filter { it.account == key || it.order.boxSnapshot?.storeId in storeIds }
            ManagedAccount(record, name, address, record.profile?.phone ?: staff?.employee?.phone ?: "",
                roles, links, customerOrders.size,
                relevantOrders.count { it.order.isPaid && it.order.cancelledAtMillis == null && it.order.collectedAtMillis == null && it.order.noShow == null })
        }.sortedWith(compareByDescending<ManagedAccount> { it.record.status == AccountStatus.SUSPENDED }.thenBy { it.name })
    }
}

