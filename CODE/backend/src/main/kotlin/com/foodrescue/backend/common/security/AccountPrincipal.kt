// AC-08: AccountPrincipal responsibility.
package com.foodrescue.backend.common.security

import java.util.UUID
data class AccountPrincipal(val accountId: UUID,val sessionId: UUID)
