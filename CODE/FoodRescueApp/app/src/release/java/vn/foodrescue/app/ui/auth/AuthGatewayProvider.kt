package vn.foodrescue.app.ui.auth

/** No local authentication bypass in release builds. */
fun createAuthGateway(): AuthGateway = object : AuthGateway {
    override var scenario = AuthScenario.SUCCESS
    private fun unavailable() = AuthReply.Error(AuthIssue.UNAVAILABLE)
    override suspend fun register(name: String, email: String, password: String) = unavailable()
    override suspend fun login(email: String, password: String) = unavailable()
    override suspend fun requestCode(email: String, purpose: AuthPurpose) = unavailable()
    override suspend fun resend(challengeId: String) = unavailable()
    override suspend fun verify(challengeId: String, code: String) = unavailable()
    override suspend fun resetPassword(grant: String, password: String) = unavailable()
}
