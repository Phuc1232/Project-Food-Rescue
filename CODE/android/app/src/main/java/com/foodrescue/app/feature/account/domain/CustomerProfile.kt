package com.foodrescue.app.feature.account.domain

internal data class CustomerProfile(val name: String, val email: String, val phone: String = "")

internal fun isValidCustomerName(name: String): Boolean =
    name.trim().length in 2..120 && name.none(Char::isISOControl)

internal fun isValidCustomerPhone(phone: String): Boolean =
    phone.isBlank() || Regex("^(0[0-9]{9}|\\+84[0-9]{9})$").matches(phone.trim())
