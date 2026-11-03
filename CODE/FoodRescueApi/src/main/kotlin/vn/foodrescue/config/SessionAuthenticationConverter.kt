package vn.foodrescue.config

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.stereotype.Component
import vn.foodrescue.repository.AuthRepository
import vn.foodrescue.service.AuthPrincipal
import java.time.Clock

/** A signed JWT is insufficient after logout, reset, account lock or refresh-token replay. */
@Component
class SessionAuthenticationConverter(private val repository: AuthRepository, private val clock: Clock) : Converter<Jwt, AbstractAuthenticationToken> {
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val accountId = jwt.subject.toIntOrNull() ?: invalid()
        val sessionId = (jwt.claims["sid"] as? Number)?.toInt() ?: invalid()
        val session = repository.session(sessionId) ?: invalid()
        val account = repository.account(accountId) ?: invalid()
        if (session.accountId != accountId || session.revokedAt != null || !clock.instant().isBefore(session.expiresAt) || account.status != "HOAT_DONG") invalid()
        return UsernamePasswordAuthenticationToken(AuthPrincipal(accountId, sessionId), null,
            listOf(SimpleGrantedAuthority("ROLE_${account.role}")))
    }
    private fun invalid(): Nothing = throw InvalidBearerTokenException("Session is no longer active")
}
