package vn.foodrescue.dto

import jakarta.validation.constraints.*

// Public registration never accepts a role, account ID or account status.
data class RegisterRequest(
    @field:NotBlank @field:Size(min = 2, max = 120) val name: String,
    @field:NotBlank @field:Email @field:Size(max = 254) val email: String,
    @field:NotBlank @field:Size(min = 8, max = 128) val password: String,
)
data class LoginRequest(
    @field:NotBlank @field:Email @field:Size(max = 254) val email: String,
    @field:NotBlank @field:Size(max = 128) val password: String,
)
data class EmailRequest(@field:NotBlank @field:Email @field:Size(max = 254) val email: String)
data class VerifyRequest(@field:Pattern(regexp = "[0-9]{6}") val code: String)
data class RefreshRequest(@field:NotBlank @field:Size(max = 128) val refreshToken: String)
data class ResetPasswordRequest(
    @field:NotBlank @field:Size(max = 128) val resetGrant: String,
    @field:NotBlank @field:Size(min = 8, max = 128) val password: String,
)
data class ProfileRequest(
    @field:NotBlank @field:Size(min = 2, max = 120) val name: String,
    @field:Pattern(regexp = "^$|^(0[0-9]{9}|\\+84[0-9]{9})$") val phone: String = "",
)
data class ProfileResponse(val id: Int, val name: String, val email: String, val phone: String?, val role: String)
data class ChallengeResponse(val id: String, val email: String, val purpose: String,
    val expiresInSeconds: Long, val resendInSeconds: Long)
data class VerificationResponse(val verified: Boolean = true, val resetGrant: String? = null)
data class SessionResponse(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long,
    val user: ProfileResponse, val tokenType: String = "Bearer")
