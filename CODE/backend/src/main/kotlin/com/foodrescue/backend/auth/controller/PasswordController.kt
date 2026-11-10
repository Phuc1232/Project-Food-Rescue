// AC-06,12,13,14,17,24: PasswordController responsibility.
package com.foodrescue.backend.auth.controller

import com.foodrescue.backend.auth.dto.*
import com.foodrescue.backend.auth.service.*
import com.foodrescue.backend.common.security.CurrentAccount
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
@RestController
@RequestMapping("/api/v1/auth")
class PasswordController(private val recovery: PasswordRecoveryService,private val change: PasswordChangeService,private val current: CurrentAccount) {
    @PostMapping("/forgot-password") @ResponseStatus(HttpStatus.ACCEPTED)
    fun forgot(@Valid @RequestBody request: EmailRequest)=recovery.forgot(request.email)
    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset(@Valid @RequestBody request: ResetPasswordRequest)=recovery.reset(request.token,request.newPassword)
    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun change(@Valid @RequestBody request: ChangePasswordRequest)=change.change(current.get(),request)
}
