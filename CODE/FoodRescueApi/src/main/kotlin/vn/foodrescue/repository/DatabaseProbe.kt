package vn.foodrescue.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class DatabaseProbe(private val jdbc: JdbcTemplate) {
    fun isAvailable(): Boolean = jdbc.queryForObject("SELECT 1", Int::class.java) == 1
}
