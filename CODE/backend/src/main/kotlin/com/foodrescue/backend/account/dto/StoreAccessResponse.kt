// AC-10,11: StoreAccessResponse responsibility.
package com.foodrescue.backend.account.dto

import java.util.UUID
data class StoreAccessResponse(val storeId: UUID,val owner: Boolean,val permissions: List<String>)
