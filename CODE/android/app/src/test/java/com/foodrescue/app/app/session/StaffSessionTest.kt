package com.foodrescue.app.app.session

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.app.navigation.*
import com.foodrescue.app.feature.staff.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StaffSessionTest {
    @Test fun staffIdentitySurvivesCustomerSwitchAndRecreationWithoutOwnerEscalation() {
        val saved = SavedStateHandle(); val session = SessionViewModel(saved)
        session.signInStaff("An", "an@example.com"); session.switchToCustomer(); session.switchToOwner()
        assertEquals(RoleNavigation.Role.CUSTOMER, session.role)
        val restored = SessionViewModel(saved); restored.switchToStaff()
        assertEquals(RoleNavigation.Role.STAFF, restored.role)
        restored.signOut(); restored.signInCustomer("Bình", "binh@example.com"); restored.switchToStaff()
        assertEquals(RoleNavigation.Role.CUSTOMER, restored.role)
        assertFalse(restored.isStaffIdentity)
    }
    @Test fun knownRevokedEmployeeRoutesToStaffAccessBoundaryInsteadOfCustomer() = runBlocking {
        val member = StoreEmployee("a", -1, "An", "an@example.com", "", pickupPermissions, EmployeeStatus.REVOKED, 2, 1, 2, emptyList())
        val repo = object : StaffAccessRepository { override suspend fun find(email: String) = StaffAccess(member, "Store", true) }
        assertEquals(RoleNavigation.Role.STAFF, accountRole("an@example.com", false, repo))
        assertEquals(RoleNavigation.Role.OWNER, accountRole("owner@example.com", true, repo))
    }
    @Test fun unavailableDirectoryNeverFallsBackToCustomerAndBypassesAccessGate() = runBlocking {
        val repo = object : StaffAccessRepository { override suspend fun find(email: String): StaffAccess? = error("Corrupt") }
        assertTrue(runCatching { accountRole("an@example.com", false, repo) }.isFailure)
        assertEquals(RoleNavigation.Role.OWNER, accountRole("owner@example.com", true, repo))
    }
    @Test fun unknownEmployeeStillUsesExistingCustomerRoute() = runBlocking {
        val repo = object : StaffAccessRepository { override suspend fun find(email: String): StaffAccess? = null }
        assertEquals(RoleNavigation.Role.CUSTOMER, accountRole("customer@example.com", false, repo))
    }
}
