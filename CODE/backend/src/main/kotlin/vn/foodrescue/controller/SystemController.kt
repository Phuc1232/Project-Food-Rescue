package vn.foodrescue.controller

import vn.foodrescue.dto.SystemStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import vn.foodrescue.service.SystemService

@RestController
@RequestMapping("/api/v1/system")
class SystemController(private val service: SystemService) {
    @GetMapping("/status")
    fun status(): SystemStatus = service.status()
}
