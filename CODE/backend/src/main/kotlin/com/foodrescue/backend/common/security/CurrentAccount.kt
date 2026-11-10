// AC-08: CurrentAccount responsibility.
package com.foodrescue.backend.common.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import com.foodrescue.backend.common.exception.authenticationFailed
@Component
class CurrentAccount {
    fun get(): AccountPrincipal = SecurityContextHolder.getContext().authentication?.principal as? AccountPrincipal ?: authenticationFailed()
}
