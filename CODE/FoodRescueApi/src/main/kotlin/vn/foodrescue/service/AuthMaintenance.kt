package vn.foodrescue.service

import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock

@Configuration
@EnableScheduling
class AuthMaintenance(private val jdbc: JdbcTemplate, private val clock: Clock) {
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    @Transactional
    fun removeExpiredChallenges() {
        // Preserve expired/revoked session rows for replay detection until the entire family has expired.
        val cutoff = Timestamp.from(clock.instant().minusSeconds(86400))
        jdbc.update("DELETE FROM gioi_han_xac_thuc WHERE het_han_luc < ?", cutoff)
        jdbc.update("DELETE FROM ma_xac_minh WHERE het_han_luc < ?", cutoff)
        jdbc.update("DELETE FROM quyen_dat_lai_mat_khau WHERE het_han_luc < ?", cutoff)
        jdbc.update("DELETE FROM phien_dang_nhap WHERE het_han_luc < ?", Timestamp.from(clock.instant().minusSeconds(7 * 86400)))
    }
}
