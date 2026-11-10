// AC-01,02: ApiException responsibility.
package com.foodrescue.backend.common.exception

import com.foodrescue.backend.common.response.FieldViolation
class ApiException(val status: Int, val code: String, override val message: String,
    val fields: List<FieldViolation> = emptyList(), val retryAfter: Long? = null) : RuntimeException(message)
fun authenticationFailed(): Nothing = throw ApiException(401, "AUTHENTICATION_FAILED", "Authentication failed")
