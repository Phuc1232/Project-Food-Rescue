package com.foodrescue.backend.order.entity

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.persistence.MutableEntity
import com.foodrescue.backend.finance.entity.FinancialPolicy
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.surprisebox.entity.SaleBatch
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "customer_order")
class CustomerOrder(
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "customer_id", nullable = false, updatable = false)
    val customer: Account,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "store_id", nullable = false, updatable = false)
    val store: Store,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "sale_batch_id", nullable = false, updatable = false)
    val saleBatch: SaleBatch,

    @field:Column(name = "order_code", nullable = false, length = 255, updatable = false)
    val orderCode: String,

    @field:Column(name = "quantity", nullable = false, updatable = false)
    val quantity: Int,

    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "status", nullable = false, length = 50)
    var status: OrderStatus,

    @field:Column(name = "hold_expires_at", nullable = false, updatable = false)
    val holdExpiresAt: Instant,

    @field:Column(name = "unit_price", nullable = false, precision = 19, scale = 2, updatable = false)
    val unitPrice: BigDecimal,

    @field:Column(name = "total_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    val totalAmount: BigDecimal,

    @field:Column(name = "minimum_value", nullable = false, precision = 19, scale = 2, updatable = false)
    val minimumValue: BigDecimal,

    @field:Column(name = "currency", nullable = false, length = 3, updatable = false)
    val currency: String,

    @field:Column(name = "safety_warnings_snapshot", nullable = false, columnDefinition = "text", updatable = false)
    val safetyWarningsSnapshot: String,

    @field:Column(name = "pickup_address_snapshot", nullable = false, columnDefinition = "text", updatable = false)
    val pickupAddressSnapshot: String,

    @field:Column(name = "pickup_starts_at", nullable = false, updatable = false)
    val pickupStartsAt: Instant,

    @field:Column(name = "pickup_ends_at", nullable = false, updatable = false)
    val pickupEndsAt: Instant,

    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "policy_id", nullable = false, updatable = false)
    val policy: FinancialPolicy,

    @field:Column(name = "commission_rate", nullable = false, precision = 9, scale = 6, updatable = false)
    val commissionRate: BigDecimal,

    @field:Column(name = "no_show_retention_rate", nullable = false, precision = 9, scale = 6, updatable = false)
    val noShowRetentionRate: BigDecimal,

    @field:Column(name = "dispute_window_hours", nullable = false, updatable = false)
    val disputeWindowHours: Int,

    @field:Column(name = "cancellation_terms_snapshot", nullable = false, columnDefinition = "text", updatable = false)
    val cancellationTermsSnapshot: String,

    @field:Column(name = "refund_terms_snapshot", nullable = false, columnDefinition = "text", updatable = false)
    val refundTermsSnapshot: String,

    @field:Column(name = "accepted_at", nullable = false, updatable = false)
    val acceptedAt: Instant,

    @field:Column(name = "pickup_code_hash", nullable = true, length = 255)
    var pickupCodeHash: String? = null,

    @field:Column(name = "completed_at", nullable = true)
    var completedAt: Instant? = null
) : MutableEntity()
