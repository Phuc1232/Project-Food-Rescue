package vn.foodrescue.service

import org.springframework.stereotype.Service
import vn.foodrescue.dto.SystemStatus
import vn.foodrescue.repository.DatabaseProbe

@Service
class SystemService(private val database: DatabaseProbe) {
    fun status(): SystemStatus {
        check(database.isAvailable()) { "Database unavailable" }
        return SystemStatus("FoodRescue", "UP", "UP")
    }
}
