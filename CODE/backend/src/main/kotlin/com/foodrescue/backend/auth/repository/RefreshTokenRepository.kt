// AC-09,21,22: RefreshTokenRepository responsibility.
package com.foodrescue.backend.auth.repository

import com.foodrescue.backend.auth.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID
interface RefreshTokenRepository : JpaRepository<RefreshToken,UUID> {
    fun findByTokenHash(tokenHash: String): RefreshToken?
}
