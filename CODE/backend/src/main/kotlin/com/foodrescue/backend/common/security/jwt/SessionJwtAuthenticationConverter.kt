// AC-07,08,11,20,21: SessionJwtAuthenticationConverter responsibility.
package com.foodrescue.backend.common.security.jwt

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.repository.AccountRoleRepository
import com.foodrescue.backend.auth.repository.LoginSessionRepository
import com.foodrescue.backend.common.security.AccountPrincipal
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.*
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import java.time.Clock
@Component
class SessionJwtAuthenticationConverter(private val sessions: LoginSessionRepository,private val roles: AccountRoleRepository,private val clock: Clock): Converter<Jwt,AbstractAuthenticationToken> {
    @Transactional(readOnly=true)
    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val id=UUID.fromString(jwt.subject); val sid=UUID.fromString(jwt.getClaimAsString("sid"))
        val s=sessions.findById(sid).orElseThrow { BadCredentialsException("Invalid session") }
        if(s.account.id!=id || s.revokedAt!=null || !s.expiresAt.isAfter(clock.instant()) ||
            s.account.status!=AccountStatus.ACTIVE || s.account.emailVerifiedAt==null) throw BadCredentialsException("Invalid session")
        return UsernamePasswordAuthenticationToken(AccountPrincipal(id,sid),null,
            roles.findAllByAccountId(id).map { SimpleGrantedAuthority("ROLE_"+it.role.name) })
    }
}
