package com.foodrescue.app.feature.order.domain

/** History for one customer; account scoping belongs to the implementation. */
internal interface OrderHistoryStore {
    fun load(): List<BoxReservation>
    fun save(orders: List<BoxReservation>)
}
