// AC-01..26: PasswordRecoveryTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import com.foodrescue.backend.auth.TokenPurpose
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class PasswordRecoveryTests: AuthTestFixtures() {
    @Test fun `forgot is generic and reset is one time and revokes all devices`() {
        register(); val first=login(); login()
        for(email in listOf("user@example.test","missing@example.test")) {
            val response=call("/api/v1/auth/forgot-password","POST",mapOf("email" to email))
            assertEquals(202,response.statusCode()); assertEquals("",response.body())
        }
        val raw=mail.token("user@example.test",TokenPurpose.RESET_PASSWORD)
        val body=mapOf("token" to raw,"newPassword" to "Replacement-password-123")
        assertEquals(204,call("/api/v1/auth/reset-password","POST",body).statusCode())
        assertEquals(400,call("/api/v1/auth/reset-password","POST",body).statusCode())
        assertEquals(401,call("/api/v1/accounts/me",token=first["accessToken"].asString()).statusCode())
        assertEquals(0,jdbc.queryForObject("select count(*) from login_session where revoked_at is null",Int::class.java))
        assertEquals(401,call("/api/v1/auth/login","POST",mapOf("email" to "user@example.test","password" to password)).statusCode())
        login(passwordValue="Replacement-password-123")
    }
    @Test fun `change requires old password and invalidates reset credentials`() {
        register(); val pair=login(); val access=pair["accessToken"].asString()
        call("/api/v1/auth/forgot-password","POST",mapOf("email" to "user@example.test"))
        val old=mail.messages.last().token
        assertEquals(401,call("/api/v1/auth/change-password","POST",mapOf("currentPassword" to "wrong","newPassword" to "Replacement-password-123"),access).statusCode())
        assertEquals(204,call("/api/v1/auth/change-password","POST",mapOf("currentPassword" to password,"newPassword" to "Replacement-password-123"),access).statusCode())
        assertEquals(400,call("/api/v1/auth/reset-password","POST",mapOf("token" to old,"newPassword" to password)).statusCode())
    }
    @Test fun `concurrent password reset succeeds once`() {
        register(); call("/api/v1/auth/forgot-password","POST",mapOf("email" to "user@example.test"))
        val body=mapOf("token" to mail.messages.last().token,"newPassword" to "Replacement-password-123")
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val tasks=(1..2).map { pool.submit<Int> { call("/api/v1/auth/reset-password","POST",body).statusCode() } }
            assertEquals(listOf(204,400),tasks.map { it.get() }.sorted())
        } finally { pool.shutdownNow() }
    }

    @Test fun `expired reset fails without changing password and new request replaces old token`() {
        register(); call("/api/v1/auth/forgot-password","POST",mapOf("email" to "user@example.test"))
        val first=mail.messages.last().token
        call("/api/v1/auth/forgot-password","POST",mapOf("email" to "user@example.test"))
        assertEquals(400,call("/api/v1/auth/reset-password","POST",mapOf("token" to first,"newPassword" to "Replacement-password-123")).statusCode())
        val second=mail.messages.last().token
        jdbc.update("update account_action_token set created_at=now()-interval '2 hours',expires_at=now()-interval '1 hour' where purpose='RESET_PASSWORD'")
        assertEquals(400,call("/api/v1/auth/reset-password","POST",mapOf("token" to second,"newPassword" to "Replacement-password-123")).statusCode())
        login()
    }
}
