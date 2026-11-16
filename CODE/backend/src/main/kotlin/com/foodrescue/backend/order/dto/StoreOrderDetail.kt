// AC-06 (T5.3): StoreOrderDetail responsibility.
package com.foodrescue.backend.order.dto

/** UC-55: accepted terms, customer display name, boxes held/allocated, amounts. */
data class StoreOrderDetail(val order: OrderView, val customerName: String, val boxes: List<AllocatedBoxView>, val money: OrderMoneyView)
