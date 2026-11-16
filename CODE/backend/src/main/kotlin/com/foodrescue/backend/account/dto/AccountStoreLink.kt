// AC-02 (T3.5): AccountStoreLink responsibility.
package com.foodrescue.backend.account.dto

import java.util.UUID

/** relation = OWNER or STAFF; assignmentStatus/permissions only for STAFF. */
data class AccountStoreLink(val storeId: UUID, val storeName: String, val storeStatus: String, val relation: String,
                            val assignmentStatus: String?, val permissions: List<String>)
