package com.foodrescue.app.feature.account

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.app.navigation.RoleNavigation
import com.foodrescue.app.app.session.SessionViewModel
import com.foodrescue.app.feature.account.domain.*
import com.foodrescue.app.feature.account.presentation.*
import org.junit.Assert.*
import org.junit.Test

class AccountManagementTest {
    private val record = AccountRecord("a".repeat(64), CustomerProfile("Đặng An", "an@example.com"))
    private val note = "Đã đối chiếu thông tin và xác nhận vi phạm."
    private val draft = AccountDecision("suspend", 0, AccountStatus.SUSPENDED, note)
    private fun suspendAccount() = record.changeStatus(draft, "admin", 1000, false, false)

    @Test fun statusChangeAppendsAuditWithoutChangingIdentity() {
        val result = suspendAccount()
        assertEquals(record.profile, result.profile)
        assertEquals(AccountStatus.SUSPENDED, result.status)
        assertEquals(1L, result.accessVersion)
        assertEquals(AccountStatusEvent("suspend", AccountStatus.SUSPENDED, note, "admin", 1000), result.events.single())
    }
    @Test fun reactivationDoesNotMakeAnOldSessionValidAgain() {
        val locked = suspendAccount()
        val active = locked.changeStatus(AccountDecision("unlock", 1, AccountStatus.ACTIVE, note), "admin", 2000, false, false)
        assertEquals(AccountStatus.ACTIVE, active.status)
        assertEquals(2L, active.accessVersion)
        assertEquals(locked.events, active.events.take(1))
    }
    @Test fun protectedAndSelfAccountsCannotBeChanged() {
        assertThrows(ProtectedAccount::class.java) { record.changeStatus(draft, "admin", 1000, true, false) }
        assertThrows(ProtectedAccount::class.java) { record.changeStatus(draft, "admin", 1000, false, true) }
    }
    @Test fun staleRevisionDuplicateIdAndSameStatusAreRejected() {
        val locked = suspendAccount()
        assertThrows(StaleAccountDecision::class.java) { locked.changeStatus(draft, "admin", 2000, false, false) }
        assertThrows(StaleAccountDecision::class.java) { locked.changeStatus(draft.copy(expectedRevision = 1, status = AccountStatus.ACTIVE), "admin", 2000, false, false) }
        assertThrows(StaleAccountDecision::class.java) { record.changeStatus(draft.copy(status = AccountStatus.ACTIVE), "admin", 2000, false, false) }
    }
    @Test fun invalidReasonsAndBrokenAuditAreRejected() {
        listOf("", "ngắn", "x".repeat(501), note + '\u0000').forEach { assertFalse(validAccountReason(it)) }
        assertTrue(validAccountReason("  $note\nĐã kiểm tra.  "))
        val locked = suspendAccount()
        assertThrows(IllegalArgumentException::class.java) { locked.copy(events = locked.events + locked.events.single()) }
        assertThrows(IllegalArgumentException::class.java) { locked.changeStatus(AccountDecision("unlock", 1, AccountStatus.ACTIVE, note), "admin", 999, false, false) }
    }
    @Test fun searchSupportsVietnameseAndCombinesRoleWithStatus() {
        val user = ManagedAccount(record, "Đặng An", "an@example.com", "0901234567", setOf(AccountRole.CUSTOMER, AccountRole.OWNER),
            listOf(AccountRelationship(AccountRole.OWNER, 1, "Bánh Mộc", "Đã duyệt")))
        val state = AdminUsersState(accounts = listOf(user), query = "dang an", role = AccountRole.OWNER, status = AccountStatus.ACTIVE)
        assertEquals(listOf(user), state.visible)
        assertEquals(listOf(user), state.copy(query = "banh moc").visible)
        assertTrue(state.copy(status = AccountStatus.SUSPENDED).visible.isEmpty())
        assertTrue(state.copy(role = AccountRole.STAFF).visible.isEmpty())
    }
    @Test fun accessVersionAndProfileSurviveSessionRestorationAndRoleSwitch() {
        val saved = SavedStateHandle()
        val session = SessionViewModel(saved)
        val profile = CustomerProfile("An Nguyễn", "an@example.com", "0901234567")
        session.signIn(AccountLogin(profile, 2), RoleNavigation.Role.OWNER)
        session.switchToCustomer()
        val restored = SessionViewModel(saved)
        assertEquals(profile, restored.profile); assertEquals(2L, restored.accessVersion)
        restored.switchToOwner(); assertEquals(2L, restored.accessVersion)
        restored.signOut(); assertNull(restored.profile); assertEquals(-1L, restored.accessVersion)
    }
    @Test fun customerFilterExcludesOperationalAccountsWithoutChangingTheirRoles() {
        val customer = ManagedAccount(record, "Khách mua hộp", "an@example.com", "", setOf(AccountRole.CUSTOMER))
        val owner = customer.copy(record = AccountRecord("b".repeat(64)), roles = setOf(AccountRole.CUSTOMER, AccountRole.OWNER))
        val staff = customer.copy(record = AccountRecord("c".repeat(64)), roles = setOf(AccountRole.CUSTOMER, AccountRole.STAFF))
        val state = AdminUsersState(accounts = listOf(customer, owner, staff))
        assertEquals(listOf(customer), state.copy(role = AccountRole.CUSTOMER).visible)
        assertEquals(listOf(owner), state.copy(role = AccountRole.OWNER).visible)
        assertEquals(listOf(staff), state.copy(role = AccountRole.STAFF).visible)
        assertTrue(AccountRole.CUSTOMER in owner.roles)
        assertTrue(AccountRole.CUSTOMER in staff.roles)
    }
    @Test fun groupCountsPartitionAccountsEvenWithMultipleRolesAndFilters() {
        val all = AccountRole.entries.mapIndexed { index, role ->
            ManagedAccount(AccountRecord(('a' + index).toString().repeat(64)), role.name, null, "",
                when (role) {
                    AccountRole.CUSTOMER -> setOf(role)
                    AccountRole.OWNER -> setOf(AccountRole.CUSTOMER, AccountRole.OWNER, AccountRole.STAFF)
                    AccountRole.STAFF -> setOf(AccountRole.CUSTOMER, AccountRole.STAFF)
                    AccountRole.ADMIN -> AccountRole.entries.toSet()
                })
        }
        val state = AdminUsersState(accounts = all, query = "not found", status = AccountStatus.SUSPENDED)
        assertTrue(state.visible.isEmpty())
        AccountRole.entries.forEach { assertEquals(1, state.countForRole(it)) }
        assertEquals(all.size, AccountRole.entries.sumOf(state::countForRole))
        assertEquals(AccountRole.OWNER, all[1].primaryRole)
        assertEquals(AccountRole.ADMIN, all[3].primaryRole)
        assertTrue(all[3].protected)
    }
    @Test fun storeAndMembershipStatusRemainSeparateFromAccountType() {
        val pending = AccountRelationship(AccountRole.OWNER, 1, "Cửa hàng", "Chờ xét duyệt")
        val revoked = AccountRelationship(AccountRole.STAFF, 1, "Cửa hàng", "Đã thu hồi quyền nhân viên")
        val owner = ManagedAccount(record, "Chủ cửa hàng", null, "", setOf(AccountRole.CUSTOMER, AccountRole.OWNER), listOf(pending))
        val staff = owner.copy(roles = setOf(AccountRole.CUSTOMER, AccountRole.STAFF), relationships = listOf(revoked))
        assertEquals(AccountRole.OWNER, owner.primaryRole)
        assertEquals(AccountRole.STAFF, staff.primaryRole)
        assertEquals("Trạng thái cửa hàng", pending.statusLabel)
        assertEquals("Quyền truy cập cửa hàng", revoked.statusLabel)
        assertEquals(AccountStatus.ACTIVE, staff.record.status)
        assertEquals("Đã thu hồi quyền nhân viên", staff.relationships.single().status)
    }
    @Test fun legacySessionRequiresFreshLoginAndStaffCannotBecomeOwner() {
        val session = SessionViewModel(SavedStateHandle())
        session.signInCustomer("An", "an@example.com"); assertEquals(-1L, session.accessVersion)
        session.signIn(AccountLogin(record.profile!!, 0), RoleNavigation.Role.STAFF)
        session.switchToOwner(); assertEquals(RoleNavigation.Role.STAFF, session.role)
    }
}
