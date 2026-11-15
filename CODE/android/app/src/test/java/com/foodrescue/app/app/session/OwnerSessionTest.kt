package com.foodrescue.app.app.session

import androidx.lifecycle.SavedStateHandle
import com.foodrescue.app.app.navigation.RoleNavigation
import org.junit.Assert.*
import org.junit.Test

class OwnerSessionTest {
    @Test fun restoredOwnerKeepsItsDestination() {
        val saved = SavedStateHandle(mapOf("customer.email" to "owner@foodrescue.vn", "session.role" to "OWNER"))
        assertEquals(RoleNavigation.Role.OWNER, SessionViewModel(saved).role)
    }

    @Test fun oldCustomerSessionStillRestoresAsCustomer() {
        val saved = SavedStateHandle(mapOf("customer.email" to "bao@example.com"))
        assertEquals(RoleNavigation.Role.CUSTOMER, SessionViewModel(saved).role)
    }

    @Test fun logoutDoesNotLeaveOwnerRoleBehind() {
        val saved = SavedStateHandle(mapOf("customer.email" to "owner@foodrescue.vn", "session.role" to "OWNER"))
        val session = SessionViewModel(saved)
        session.signOut()
        assertNull(SessionViewModel(saved).role)
        assertTrue(saved.keys().isEmpty())
    }
}
