package com.foodrescue.app.feature.order.presentation.component

import com.foodrescue.app.feature.order.domain.OrderStatus

internal val OrderStatus.label: String get() = when (this) {
    OrderStatus.WAITING_PAYMENT -> "Chờ thanh toán"
    OrderStatus.CHECKING_PAYMENT -> "Đang xác nhận thanh toán"
    OrderStatus.WAITING_PICKUP -> "Chờ nhận hộp"
    OrderStatus.CANCELLATION_REQUESTED -> "Chờ xử lý hủy"
    OrderStatus.COLLECTED -> "Đã nhận hộp"
    OrderStatus.CANCELLED -> "Đã hủy"
    OrderStatus.EXPIRED -> "Hết hạn thanh toán"
    OrderStatus.NO_SHOW -> "Không đến nhận"
}
