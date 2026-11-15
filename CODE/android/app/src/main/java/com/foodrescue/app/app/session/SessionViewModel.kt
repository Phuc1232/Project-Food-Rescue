package com.foodrescue.app.app.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.foodrescue.app.app.navigation.RoleNavigation
import com.foodrescue.app.feature.account.domain.CustomerProfile
import com.foodrescue.app.feature.account.domain.isValidCustomerName
import com.foodrescue.app.feature.account.domain.isValidCustomerPhone

/** Owns the frontend account session. Passwords and OTP values are never retained here. */
class SessionViewModel(private val saved: SavedStateHandle) : ViewModel() {
    internal var profile by mutableStateOf(
        saved.get<String>(EMAIL)?.takeIf { it.isNotBlank() }?.let { email ->
            CustomerProfile(saved[NAME] ?: email.substringBefore('@'), email, saved[PHONE] ?: "")
        },
    )
        private set

    private var selectedRole by mutableStateOf(
        saved.get<String>(ROLE)?.let { runCatching { RoleNavigation.Role.valueOf(it) }.getOrNull() }
            ?: RoleNavigation.Role.CUSTOMER,
    )
    val role: RoleNavigation.Role? get() = profile?.let { selectedRole }
    internal var isStaffIdentity by mutableStateOf(saved.get<Boolean>(STAFF_IDENTITY) ?: false)
        private set

    fun signInCustomer(name: String, email: String) {
        require(email.isNotBlank())
        isStaffIdentity = false
        selectedRole = RoleNavigation.Role.CUSTOMER
        save(CustomerProfile(name.trim().ifBlank { email.trim().substringBefore('@') }, email.trim()))
    }

    fun signInOwner(name: String, email: String) {
        require(email.isNotBlank())
        isStaffIdentity = false
        selectedRole = RoleNavigation.Role.OWNER
        save(CustomerProfile(name.trim().ifBlank { email.trim().substringBefore('@') }, email.trim()))
    }

    fun signInStaff(name: String, email: String) {
        require(email.isNotBlank())
        isStaffIdentity = true
        selectedRole = RoleNavigation.Role.STAFF
        save(CustomerProfile(name.trim().ifBlank { email.trim().substringBefore('@') }, email.trim()))
    }
    fun switchToStaff() { if (isStaffIdentity) profile?.let { selectedRole = RoleNavigation.Role.STAFF; save(it) } }
    fun switchToOwner() { if (!isStaffIdentity) profile?.let { selectedRole = RoleNavigation.Role.OWNER; save(it) } }
    fun switchToCustomer() { profile?.let { selectedRole = RoleNavigation.Role.CUSTOMER; save(it) } }

    fun updateProfile(name: String, phone: String): Boolean {
        val current = profile ?: return false
        if (!isValidCustomerName(name) || !isValidCustomerPhone(phone)) return false
        save(current.copy(name = name.trim(), phone = phone.trim()))
        return true
    }

    fun signOut() {
        profile = null
        saved.remove<String>(NAME)
        saved.remove<String>(EMAIL)
        saved.remove<String>(PHONE)
        saved.remove<String>(ROLE)
        saved.remove<Boolean>(STAFF_IDENTITY)
        isStaffIdentity = false
        selectedRole = RoleNavigation.Role.CUSTOMER
    }

    private fun save(value: CustomerProfile) {
        profile = value
        saved[NAME] = value.name
        saved[EMAIL] = value.email
        saved[PHONE] = value.phone
        saved[ROLE] = selectedRole.name
        saved[STAFF_IDENTITY] = isStaffIdentity
    }

    private companion object {
        const val NAME = "customer.name"
        const val EMAIL = "customer.email"
        const val PHONE = "customer.phone"
        const val ROLE = "session.role"
        const val STAFF_IDENTITY = "session.staffIdentity"
    }
}
