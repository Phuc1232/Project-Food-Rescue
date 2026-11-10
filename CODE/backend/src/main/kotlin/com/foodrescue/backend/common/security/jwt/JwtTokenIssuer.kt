// AC-07,08,11,20,21: JwtTokenIssuer responsibility.
package com.foodrescue.backend.common.security.jwt

import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.stereotype.Component
import java.time.*
import java.util.UUID
import com.foodrescue.backend.auth.entity.LoginSession
@Component
class JwtTokenIssuer(private val encoder: JwtEncoder,private val p: JwtProperties,private val clock: Clock) {
    fun issue(session: LoginSession): Pair<String,Instant> {
        val now=clock.instant(); val expiry=minOf(now.plus(p.accessTtl),session.expiresAt)
        val claims=JwtClaimsSet.builder().subject(session.account.id.toString()).claim("sid",session.id.toString())
            .id(UUID.randomUUID().toString()).issuer(p.issuer).audience(listOf(p.audience))
            .issuedAt(now).notBefore(now).expiresAt(expiry).build()
        val header=JwsHeader.with(SignatureAlgorithm.RS256).keyId(p.keyId).build()
        return encoder.encode(JwtEncoderParameters.from(header,claims)).tokenValue to expiry
    }
}
