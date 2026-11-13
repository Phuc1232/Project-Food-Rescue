// AC-07,13: AccountSummary responsibility.
package com.foodrescue.backend.store.dto

import java.util.UUID

data class AccountSummary(val id: UUID, val email: String?, val displayName: String)
