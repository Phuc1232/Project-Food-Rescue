package vn.foodrescue.app.data.remote

import retrofit2.http.*

data class RegisterBody(val name: String, val email: String, val password: String)
data class LoginBody(val email: String, val password: String)
data class EmailBody(val email: String)
data class VerifyBody(val code: String)
data class RefreshBody(val refreshToken: String)
data class ResetPasswordBody(val resetGrant: String, val password: String)
data class ProfileBody(val name: String, val phone: String)
data class AccountProfile(val id: Int, val name: String, val email: String, val phone: String?, val role: String)
data class SessionResult(val accessToken: String, val refreshToken: String, val expiresInSeconds: Long, val user: AccountProfile)
data class ChallengeResult(val id: String, val email: String, val purpose: String, val expiresInSeconds: Long, val resendInSeconds: Long)
data class VerificationResult(val verified: Boolean, val resetGrant: String?)
data class RemoteError(val code: String?, val message: String?)

interface AuthApi {
    @POST("api/v1/auth/register") suspend fun register(@Body body: RegisterBody): ChallengeResult
    @POST("api/v1/auth/login") suspend fun login(@Body body: LoginBody): SessionResult
    @POST("api/v1/auth/email-verifications") suspend fun emailVerification(@Body body: EmailBody): ChallengeResult
    @POST("api/v1/auth/password-reset-requests") suspend fun passwordResetRequest(@Body body: EmailBody): ChallengeResult
    @POST("api/v1/auth/challenges/{id}/verify") suspend fun verify(@Path("id") id: String, @Body body: VerifyBody): VerificationResult
    @POST("api/v1/auth/challenges/{id}/resend") suspend fun resend(@Path("id") id: String): ChallengeResult
    @POST("api/v1/auth/password-resets") suspend fun resetPassword(@Body body: ResetPasswordBody)
    @POST("api/v1/auth/refresh") suspend fun refresh(@Body body: RefreshBody): SessionResult
    @POST("api/v1/auth/logout") suspend fun logout(@Header("Authorization") authorization: String)
    @GET("api/v1/me") suspend fun me(@Header("Authorization") authorization: String): AccountProfile
    @PATCH("api/v1/me") suspend fun updateMe(@Header("Authorization") authorization: String, @Body body: ProfileBody): AccountProfile
}
