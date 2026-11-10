// AC-05,06,16,17,19,23: RegistrationController responsibility.
package com.foodrescue.backend.auth.controller

import com.foodrescue.backend.auth.dto.*
import com.foodrescue.backend.auth.service.RegistrationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
@RestController
@RequestMapping("/api/v1/auth")
class RegistrationController(private val service: RegistrationService) {
    @PostMapping("/register") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: RegisterRequest)=service.register(request)
}
