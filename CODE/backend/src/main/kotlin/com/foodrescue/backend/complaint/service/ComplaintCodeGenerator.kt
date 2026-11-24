// AC-01..11 (T8.1): ComplaintCodeGenerator responsibility.
package com.foodrescue.backend.complaint.service

import com.foodrescue.backend.complaint.repository.ComplaintRepository
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** "CMP-yyMMdd-XXXXXX" (Vietnam date, base32 without ambiguous characters); unique complaint reception code. */
@Component
class ComplaintCodeGenerator(private val complaints: ComplaintRepository, private val clock: Clock) {
    private val random = SecureRandom()

    fun next(): String {
        val day = clock.instant().atZone(VN).format(DAY)
        repeat(5) {
            val code = "CMP-$day-" + (1..6).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
            if (!complaints.existsByComplaintCode(code)) return code
        }
        error("Could not generate a unique complaint code")
    }

    private companion object {
        const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val VN: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMdd")
    }
}
