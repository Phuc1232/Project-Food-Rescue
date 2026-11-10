// AC-06,13,20,24: TokenPairResponse responsibility.
package com.foodrescue.backend.auth.dto

import java.time.Instant
class TokenPairResponse(val accessToken: String,val expiresAt: Instant,val refreshToken: String,
    val refreshExpiresAt: Instant,val tokenType: String = "Bearer")
