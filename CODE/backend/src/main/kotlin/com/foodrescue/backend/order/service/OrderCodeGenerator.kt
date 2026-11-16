// AC-02 (T5.2): OrderCodeGenerator responsibility.
package com.foodrescue.backend.order.service

import com.foodrescue.backend.order.repository.CustomerOrderRepository
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "FR-yyMMdd-XXXXXX" (Vietnam date, base32 without ambiguous characters); unique (BR-T52-06). Used as the payment reference later. */
@Component
class OrderCodeGenerator(private val orders: CustomerOrderRepository, private val clock: Clock) {
    private val random = SecureRandom()

    fun next(): String {
        val day = clock.instant().atZone(VN).format(DAY)
        repeat(5) {
            val code = "FR-$day-" + (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
            if (!orders.existsByOrderCode(code)) return code
        }
        error("Could not generate a unique order code")
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val VN: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")
    }
}
