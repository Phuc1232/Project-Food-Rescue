package com.foodrescue.app.feature.auth.domain

/** Local frontend identity only. The backend must supply and enforce authenticated roles. */
internal object AdminIdentity {
    private const val EMAIL = "admin@foodrescue.vn"
    fun matches(email: String): Boolean = email.trim().equals(EMAIL, ignoreCase = true)
}
