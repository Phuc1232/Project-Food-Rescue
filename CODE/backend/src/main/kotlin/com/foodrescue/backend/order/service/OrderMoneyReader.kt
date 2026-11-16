// AC-05,06 (T5.3): OrderMoneyReader responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.finance.service.MoneyRounding
import com.foodrescue.backend.order.dto.OrderMoneyView
import com.foodrescue.backend.order.entity.CustomerOrder
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal

/** UC-22: amounts of an order. Verified payments and refunds come from phase 6 tables (zero until then). */
@Component
class OrderMoneyReader(private val jdbc: JdbcTemplate) {
    fun money(o: CustomerOrder): OrderMoneyView {
        val paid = sum("SELECT coalesce(sum(received_amount),0) FROM payment WHERE order_id = ? AND status = 'VERIFIED'", o)
        val refunded = sum("SELECT coalesce(sum(amount),0) FROM refund WHERE order_id = ? AND status = 'SUCCEEDED'", o)
        // T6.4: open refund obligations (refund rows are created when an administrator pays them out, T6.5)
        val refunding = sum("SELECT coalesce(sum(coalesce(approved_amount, requested_amount)),0) FROM refund_request WHERE order_id = ? AND status IN ('PENDING','APPROVED')", o)
        return OrderMoneyView(o.id!!, o.totalAmount.setScale(0), paid, (o.totalAmount - paid).max(BigDecimal.ZERO).setScale(0), refunded, refunding,
            MoneyRounding.applyRate(o.totalAmount, o.noShowRetentionRate), o.currency)
    }

    private fun sum(sql: String, o: CustomerOrder): BigDecimal = jdbc.queryForObject(sql, BigDecimal::class.java, o.id)!!.setScale(0)
}
