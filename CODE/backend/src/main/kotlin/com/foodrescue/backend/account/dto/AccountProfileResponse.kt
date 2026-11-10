// AC-08,10,11: AccountProfileResponse responsibility.
package com.foodrescue.backend.account.dto

import java.util.UUID
data class AccountProfileResponse(val id: UUID,val email: String,val displayName: String,val roles: List<String>,val stores: List<StoreAccessResponse>)
