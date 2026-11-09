package com.foodrescue.app.dashboard.customer

internal data class CustomerNavigation(val tab: Int = 0, val path: List<String> = emptyList()) {
    val current: String? get() = path.lastOrNull()
    fun open(destination: String): CustomerNavigation {
        val existing = path.indexOf(destination)
        return copy(path = if (existing >= 0) path.take(existing + 1) else path + destination)
    }
    fun back() = if (path.isNotEmpty()) copy(path = path.dropLast(1)) else copy(tab = 0)
    fun selectTab(index: Int): CustomerNavigation {
        require(index in 0..3)
        return CustomerNavigation(index)
    }
}
