// AC-07,08 (T7.1): PickupCodeVerifyRequest carries the credential in the request body.
package com.foodrescue.backend.pickup.dto

// Format validation is performed AFTER rate limiting, including null/empty/oversized strings.
data class PickupCodeVerifyRequest(val code: String? = null)
