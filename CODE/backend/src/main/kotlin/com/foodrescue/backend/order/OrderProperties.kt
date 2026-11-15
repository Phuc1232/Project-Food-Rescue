// AC-02 (T5.2): OrderProperties responsibility.
package com.foodrescue.backend.order

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** orders.hold-minutes: how long held boxes wait for payment (BR-23, roadmap D5). */
@Component
@ConfigurationProperties("orders")
class OrderProperties {
    var holdMinutes: Long = 10
    var maxQuantity: Int = 5
}
