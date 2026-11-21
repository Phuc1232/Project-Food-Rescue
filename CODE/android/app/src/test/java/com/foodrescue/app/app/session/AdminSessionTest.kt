package com.foodrescue.app.app.session

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.R
import com.foodrescue.app.app.navigation.*
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.auth.presentation.*
import com.foodrescue.app.feature.staff.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdminSessionTest {
    @Test fun exactReservedIdentityRoutesToAdminBeforeOtherRoles() = runBlocking {
        val directory = object : StaffAccessRepository { override suspend fun find(email: String): StaffAccess? = error("Not needed") }
        assertEquals(RoleNavigation.Role.ADMIN, accountRole(" ADMIN@FOODRESCUE.VN ", true, directory))
        assertFalse(AdminIdentity.matches("not-admin@foodrescue.vn"))
        assertFalse(AdminIdentity.matches("admin@foodrescue.vn.example.com"))
    }
    @Test fun customerCannotEnterAdminSessionAndAdminCannotSwitchIntoPartnerRole() {
        val saved = SavedStateHandle(); val session = SessionViewModel(saved)
        assertThrows(IllegalArgumentException::class.java) { session.signInAdmin("Customer", "customer@example.com") }
        session.signInAdmin("Admin", "admin@foodrescue.vn")
        session.switchToCustomer(); session.switchToOwner()
        assertEquals(RoleNavigation.Role.ADMIN, session.role)
        assertEquals(RoleNavigation.Role.ADMIN, SessionViewModel(saved).role)
        session.signOut(); assertNull(session.role)
    }
    @Test fun registrationDoesNotCreateAdministratorIdentity() {
        val state = AuthUiState(page = AuthPage.REGISTER, form = AuthFormState(name = "Admin", email = "admin@foodrescue.vn",
            password = "Password123", confirmation = "Password123"))
        val result = AuthFlow.submit(state, 100)
        assertEquals(AuthPage.REGISTER, result.page)
        assertEquals(R.string.auth_error_reserved_account, result.errors[AuthField.EMAIL])
    }
}
