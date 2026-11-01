package vn.foodrescue.system.application

import org.springframework.stereotype.Service
import vn.foodrescue.system.api.SystemStatus
import vn.foodrescue.system.persistence.DatabaseProbe

@Service
class SystemService(private val database: DatabaseProbe) {
    fun status(): SystemStatus {
        check(database.isAvailable()) { "Database unavailable" }
        return SystemStatus("FoodRescue", "UP", "UP")
    }
}
