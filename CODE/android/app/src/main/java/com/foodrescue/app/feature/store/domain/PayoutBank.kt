package com.foodrescue.app.feature.store.domain

import com.foodrescue.app.core.util.searchKey

internal data class PayoutBank(val code: String, val name: String, val fullName: String, val aliases: List<String> = emptyList()) {
    fun matches(value: String): Boolean = (listOf(code, name) + aliases).any { it.equals(value.trim(), ignoreCase = true) }
    fun matchesQuery(query: String): Boolean = (listOf(code, name, fullName) + aliases).joinToString(" ").searchKey().contains(query.trim().searchKey())
}

internal fun List<PayoutBank>.findBank(value: String): PayoutBank? = firstOrNull { it.matches(value) }
