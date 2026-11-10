// AC-01..26: JwtSecurityTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.util.UUID
class JwtSecurityTests: AuthTestFixtures() {
    @Autowired lateinit var encoder: JwtEncoder
    private fun signed(sub: String,sid: String?,issuer: String="foodrescue",audience: String="foodrescue-api",expired: Boolean=false): String {
        val now=Instant.now(); val start=if(expired) now.minusSeconds(600) else now
        val b=JwtClaimsSet.builder().subject(sub).issuer(issuer).audience(listOf(audience)).id(UUID.randomUUID().toString())
            .issuedAt(start).notBefore(start).expiresAt(start.plusSeconds(300))
        sid?.let { b.claim("sid",it) }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-key").build(),b.build())).tokenValue
    }
    @Test fun `reject signature issuer audience expired missing claims wrong uuid and foreign session`() {
        register(); val access=login()["accessToken"].asString()
        val id=jdbc.queryForObject("select id from account",UUID::class.java)!!.toString()
        val sid=jdbc.queryForObject("select id from login_session",UUID::class.java)!!.toString()
        val parts=access.split('.'); val forged=parts[0]+"."+parts[1]+"."+(if(parts[2][0]=='A') "B" else "A")+parts[2].drop(1)
        val cases=listOf(forged,"eyJhbGciOiJub25lIn0.e30.",signed(id,sid,issuer="wrong"),signed(id,sid,audience="wrong"),
            signed(id,sid,expired=true),signed(id,null),signed("bad",sid),signed(id,"bad"),signed(UUID.randomUUID().toString(),sid))
        cases.forEach { assertEquals(401,call("/api/v1/accounts/me",token=it).statusCode()) }
        jdbc.update("update account set status='LOCKED'")
        assertEquals(401,call("/api/v1/accounts/me",token=access).statusCode())
    }
}
