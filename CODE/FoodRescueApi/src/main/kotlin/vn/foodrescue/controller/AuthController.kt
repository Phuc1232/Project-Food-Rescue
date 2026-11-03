package vn.foodrescue.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import vn.foodrescue.dto.*
import vn.foodrescue.service.AuthPrincipal
import vn.foodrescue.service.AuthService
import vn.foodrescue.service.AuthRequestGuard

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val service: AuthService, private val guard: AuthRequestGuard) {
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody body: RegisterRequest, request: HttpServletRequest): ChallengeResponse {
        guard.mail(body.email, request.remoteAddr); return service.register(body)
    }
    @PostMapping("/email-verifications")
    fun verification(@Valid @RequestBody body: EmailRequest, request: HttpServletRequest): ChallengeResponse {
        guard.mail(body.email, request.remoteAddr); return service.requestCode(body.email, "VERIFY_EMAIL")
    }
    @PostMapping("/password-reset-requests")
    fun resetRequest(@Valid @RequestBody body: EmailRequest, request: HttpServletRequest): ChallengeResponse {
        guard.mail(body.email, request.remoteAddr); return service.requestCode(body.email, "RESET_PASSWORD")
    }
    @PostMapping("/challenges/{id}/verify")
    fun verify(@PathVariable id: String, @Valid @RequestBody body: VerifyRequest, request: HttpServletRequest): VerificationResponse {
        guard.challenge(id, request.remoteAddr, false); return service.verify(id, body.code)
    }
    @PostMapping("/challenges/{id}/resend")
    fun resend(@PathVariable id: String, request: HttpServletRequest): ChallengeResponse {
        guard.challenge(id, request.remoteAddr, true); return service.resend(id)
    }
    @PostMapping("/login")
    fun login(@Valid @RequestBody body: LoginRequest, request: HttpServletRequest): SessionResponse {
        guard.login(body.email, request.remoteAddr); return service.login(body)
    }
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody body: RefreshRequest, request: HttpServletRequest): SessionResponse {
        guard.refresh(request.remoteAddr); return service.refresh(body.refreshToken)
    }
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@AuthenticationPrincipal principal: AuthPrincipal) = service.logout(principal)
    @PostMapping("/password-resets") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetPassword(@Valid @RequestBody body: ResetPasswordRequest, request: HttpServletRequest) {
        guard.reset(request.remoteAddr); service.resetPassword(body)
    }
}
