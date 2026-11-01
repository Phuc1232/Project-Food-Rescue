package vn.foodrescue.system.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import vn.foodrescue.system.application.SystemService

@RestController
@RequestMapping("/api/v1/system")
class SystemController(private val service: SystemService) {
    @GetMapping("/status")
    fun status(): SystemStatus = service.status()
}
data class SystemStatus(val service: String, val status: String, val database: String)
