// AC-08,09,21,22: SessionResponse responsibility.
package com.foodrescue.backend.auth.dto

import java.time.Instant
import java.util.UUID
data class SessionResponse(val id: UUID,val createdAt: Instant,val expiresAt: Instant,val revokedAt: Instant?,val current: Boolean)
