// AC-01,13: StoreSummary responsibility.
package com.foodrescue.backend.store.dto

import java.util.UUID

data class StoreSummary(val id: UUID, val name: String, val status: String)
