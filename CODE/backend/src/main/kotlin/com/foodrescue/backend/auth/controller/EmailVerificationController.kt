// AC-13,23,24: EmailVerificationController responsibility.
package com.foodrescue.backend.auth.controller

import com.foodrescue.backend.auth.dto.*
import com.foodrescue.backend.auth.service.EmailVerificationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
@RestController
@RequestMapping("/api/v1/auth")
class EmailVerificationController(private val service: EmailVerificationService) {
    @PostMapping("/verify-email") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun verify(@Valid @RequestBody request: ActionTokenRequest)=service.verify(request.token)
    @PostMapping("/resend-verification") @ResponseStatus(HttpStatus.ACCEPTED)
    fun resend(@Valid @RequestBody request: EmailRequest)=service.resend(request.email)
}
