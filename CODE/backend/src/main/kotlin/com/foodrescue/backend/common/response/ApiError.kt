// AC-01,02: ApiError responsibility.
package com.foodrescue.backend.common.response

import java.time.Instant
import java.util.UUID
data class ApiError(val code: String, val message: String, val requestId: String,
    val timestamp: Instant = Instant.now(), val fieldErrors: List<FieldViolation> = emptyList())
