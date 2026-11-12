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

/** Owns the local customer UI session. Passwords and OTP values are never retained here. */
class SessionViewModel(private val saved: SavedStateHandle) : ViewModel() {
    internal var profile by mutableStateOf(
        saved.get<String>(EMAIL)?.takeIf { it.isNotBlank() }?.let { email ->
            CustomerProfile(saved[NAME] ?: email.substringBefore('@'), email, saved[PHONE] ?: "")
        },
    )
        private set

    val role: RoleNavigation.Role? get() = profile?.let { RoleNavigation.Role.CUSTOMER }

    fun signInCustomer(name: String, email: String) {
        require(email.isNotBlank())
        save(CustomerProfile(name.trim().ifBlank { email.trim().substringBefore('@') }, email.trim()))
    }

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
    }

    private fun save(value: CustomerProfile) {
        profile = value
        saved[NAME] = value.name
        saved[EMAIL] = value.email
        saved[PHONE] = value.phone
    }

    private companion object {
        const val NAME = "customer.name"
        const val EMAIL = "customer.email"
        const val PHONE = "customer.phone"
    }
}
