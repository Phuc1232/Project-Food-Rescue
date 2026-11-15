package com.foodrescue.app.feature.staff.domain

import org.junit.Assert.*
import org.junit.Test

class EmployeeRulesTest {
    private val valid = EmployeeDraft(name = "Nguyễn An", email = "an@example.com", permissions = setOf(EmployeePermission.VIEW_ORDERS))
    private fun employee() = StoreEmployee("staff-a", -1, "Nguyễn Ánh", "anh@example.com", "0901234567",
        setOf(EmployeePermission.VIEW_ORDERS, EmployeePermission.HAND_OVER), EmployeeStatus.ACTIVE, 1, 100, 100,
        listOf(EmployeeEvent(EmployeeEventType.CREATED, 100, setOf(EmployeePermission.VIEW_ORDERS, EmployeePermission.HAND_OVER))))
    @Test fun invalidIdentityIsRejectedBeforeCreatingAccount() {
        assertEquals(setOf(EmployeeIssue.NAME, EmployeeIssue.EMAIL, EmployeeIssue.PHONE, EmployeeIssue.PERMISSIONS),
            EmployeeDraft(name = "a", email = "wrong@", phone = "123").validationIssues())
        assertTrue(valid.validationIssues().isEmpty())
        assertTrue(valid.copy(name = "An\nNguyễn").validationIssues().contains(EmployeeIssue.NAME))
        assertTrue(valid.copy(email = "a..b@example.com").validationIssues().contains(EmployeeIssue.EMAIL))
    }
    @Test fun optionalPhoneAndEmailNormalizationPreserveValidInput() {
        assertEquals("an@example.com", employeeEmail(" An@Example.COM "))
        assertTrue(valid.copy(phone = "+84901234567").validationIssues().isEmpty())
        assertTrue(valid.copy(phone = "").validationIssues().isEmpty())
    }
    @Test fun dependentPermissionsAddViewAndRemovingViewRemovesDependents() {
        val handover = emptySet<EmployeePermission>().toggle(EmployeePermission.HAND_OVER, true)
        assertEquals(setOf(EmployeePermission.VIEW_ORDERS, EmployeePermission.HAND_OVER), handover)
        val all = EmployeePermission.entries.toSet().toggle(EmployeePermission.VIEW_ORDERS, false)
        assertEquals(setOf(EmployeePermission.OPERATE_SALES), all)
        assertEquals(setOf(EmployeePermission.VIEW_ORDERS), handover.toggle(EmployeePermission.HAND_OVER, false))
    }
    @Test fun directInvalidPermissionSetCannotBypassFormRules() {
        assertTrue(valid.copy(permissions = setOf(EmployeePermission.PREPARE_BOXES)).validationIssues().contains(EmployeeIssue.PERMISSIONS))
    }
    @Test fun revocationPreservesHistoryButRemovesEffectiveAccess() {
        val before = employee()
        val after = before.changeAccess(1, EmployeeStatus.REVOKED, "Nghỉ việc", 200)
        assertEquals(EmployeeStatus.REVOKED, after.status)
        assertTrue(after.effectivePermissions.isEmpty())
        assertEquals(before.permissions, after.permissions)
        assertEquals(2, after.revision)
        assertEquals(2, after.events.size)
        assertEquals("Nghỉ việc", after.events.last().note)
        val restored = after.changeAccess(2, EmployeeStatus.ACTIVE, "", 300)
        assertEquals(before.permissions, restored.effectivePermissions)
        assertEquals(EmployeeEventType.RESTORED, restored.events.last().type)
    }
    @Test fun staleRevisionCannotRestoreOrRevoke() {
        val revoked = employee().changeAccess(1, EmployeeStatus.REVOKED, "Nghỉ việc", 200)
        assertEquals(EmployeeIssue.STALE, assertThrows(EmployeeException::class.java) {
            revoked.changeAccess(1, EmployeeStatus.ACTIVE, "", 300)
        }.issue)
    }
    @Test fun revokeRequiresReasonAndRepeatedActionIsIdempotent() {
        assertEquals(EmployeeIssue.REASON, assertThrows(EmployeeException::class.java) {
            employee().changeAccess(1, EmployeeStatus.REVOKED, " ", 200)
        }.issue)
        val revoked = employee().changeAccess(1, EmployeeStatus.REVOKED, "Nghỉ việc", 200)
        assertEquals(revoked, revoked.changeAccess(2, EmployeeStatus.REVOKED, "Nghỉ việc", 300))
    }
    @Test fun searchUsesVietnameseNamesEmailsPhonesAndStatus() {
        val active = employee()
        val revoked = active.copy(id = "b", name = "Đặng Bình", email = "binh@example.com", status = EmployeeStatus.REVOKED)
        val list = listOf(active, revoked)
        assertEquals(listOf(active), list.searchEmployees("nguyen anh", EmployeeFilter.ALL))
        assertEquals(listOf(revoked), list.searchEmployees("dang", EmployeeFilter.REVOKED))
        assertTrue(list.searchEmployees("dang", EmployeeFilter.ACTIVE).isEmpty())
        assertTrue(list.searchEmployees("no-match", EmployeeFilter.ALL).isEmpty())
        assertEquals(2, list.searchEmployees("0901", EmployeeFilter.ALL).size)
    }
    @Test fun suspendedStoreNeverExposesEmployeePermissions() {
        val access = StaffAccess(employee(), "Cửa hàng", false)
        assertFalse(access.allowed)
        assertTrue(access.permissions.isEmpty())
    }
}
