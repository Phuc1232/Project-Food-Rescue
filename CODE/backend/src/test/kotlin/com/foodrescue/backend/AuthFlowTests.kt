// AC-01..26: AuthFlowTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class AuthFlowTests: AuthTestFixtures() {
    @Test fun `register verify login me logout revokes access immediately`() {
        register(); val pair=login(); val access=pair["accessToken"].asString()
        val me=call("/api/v1/accounts/me",token=access); assertEquals(200,me.statusCode()); assertEquals("user@example.test",json(me)["email"].asString())
        assertFalse(me.body().contains("passwordHash")); assertFalse(jdbc.queryForObject("select password_hash from account",String::class.java)!!.contains(password))
        assertEquals(204,call("/api/v1/auth/logout","POST",token=access).statusCode())
        assertEquals(401,call("/api/v1/accounts/me",token=access).statusCode())
        assertEquals(401,refresh(pair["refreshToken"].asString()).statusCode())
        assertTrue(jdbc.queryForObject("select count(*) from audit_log",Int::class.java)!!>=4)
        assertEquals(0,jdbc.queryForObject("select count(*) from audit_log where after_state like '%password%'",Int::class.java))
    }
    @Test fun `pending and locked accounts cannot login and registration cannot inject admin`() {
        register(verify=false)
        val body=mapOf("email" to "user@example.test","password" to password)
        assertEquals(401,call("/api/v1/auth/login","POST",body).statusCode())
        assertEquals(400,call("/api/v1/auth/register","POST",body+mapOf("displayName" to "Admin","role" to "ADMIN")).statusCode())
        assertEquals("CUSTOMER",jdbc.queryForObject("select role from account_role",String::class.java))
    }
    @Test fun `resend invalidates earlier token and failed smtp can be retried`() {
        register(verify=false); val first=mail.messages.last().token
        mail.fail=true
        assertEquals(202,call("/api/v1/auth/resend-verification","POST",mapOf("email" to "user@example.test")).statusCode())
        assertEquals(400,call("/api/v1/auth/verify-email","POST",mapOf("token" to first)).statusCode())
        mail.fail=false
        assertEquals(202,call("/api/v1/auth/resend-verification","POST",mapOf("email" to "user@example.test")).statusCode())
        assertEquals(204,call("/api/v1/auth/verify-email","POST",mapOf("token" to mail.messages.last().token)).statusCode())
    }
    @Test fun `concurrent duplicate registrations persist one account and role`() {
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val tasks=(1..2).map { pool.submit<Int> { call("/api/v1/auth/register","POST",mapOf("email" to "same@example.test","password" to password,"displayName" to "User")).statusCode() } }
            assertEquals(listOf(201,409),tasks.map { it.get() }.sorted())
            assertEquals(1,jdbc.queryForObject("select count(*) from account_role",Int::class.java))
        } finally { pool.shutdownNow() }
    }

    @Test fun `expired and locked verification cannot activate account`() {
        register(verify=false); val token=mail.messages.last().token
        jdbc.update("update account_action_token set created_at=now()-interval '2 days',expires_at=now()-interval '1 day'")
        assertEquals(400,call("/api/v1/auth/verify-email","POST",mapOf("token" to token)).statusCode())
        call("/api/v1/auth/resend-verification","POST",mapOf("email" to "user@example.test"))
        val replacement=mail.messages.last().token; jdbc.update("update account set status='LOCKED'")
        assertEquals(400,call("/api/v1/auth/verify-email","POST",mapOf("token" to replacement)).statusCode())
        assertEquals("LOCKED",jdbc.queryForObject("select status from account",String::class.java))
    }
    @Test fun `audit persistence failure rolls back account role and mail`() {
        jdbc.execute("CREATE FUNCTION test_fail_audit() RETURNS trigger LANGUAGE plpgsql AS \$\$ BEGIN RAISE EXCEPTION 'simulated audit failure' USING ERRCODE='23514'; END; \$\$")
        jdbc.execute("CREATE TRIGGER test_fail_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION test_fail_audit()")
        try {
            val r=call("/api/v1/auth/register","POST",mapOf("email" to "rollback@example.test","password" to password,"displayName" to "User"))
            assertEquals(409,r.statusCode()); assertEquals(0,jdbc.queryForObject("select count(*) from account",Int::class.java))
            assertEquals(0,jdbc.queryForObject("select count(*) from account_role",Int::class.java)); assertTrue(mail.messages.isEmpty())
        } finally { jdbc.execute("DROP TRIGGER test_fail_audit ON audit_log"); jdbc.execute("DROP FUNCTION test_fail_audit()") }
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension::class)
    fun `credential and token values are absent from application logs`(output: org.springframework.boot.test.system.CapturedOutput) {
        register(); val verification=mail.messages.last().token; val pair=login()
        call("/api/v1/auth/forgot-password","POST",mapOf("email" to "user@example.test"))
        val reset=mail.messages.last().token
        mail.fail=true; call("/api/v1/auth/forgot-password","POST",mapOf("email" to "user@example.test"))
        val hash=jdbc.queryForObject("select password_hash from account",String::class.java)!!
        for(secret in listOf(password,verification,reset,hash,pair["accessToken"].asString(),pair["refreshToken"].asString()))
            assertFalse(output.toString().contains(secret))
    }
}
