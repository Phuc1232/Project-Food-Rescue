// AC-09,21: persistence support for verified accounts and controlled sessions.
package com.foodrescue.backend.auth.repository

import com.foodrescue.backend.auth.entity.LoginSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LoginSessionRepository : JpaRepository<LoginSession, UUID> {
    fun findAllByAccountId(accountId: UUID): List<LoginSession>
    fun findAllByAccountId(accountId: UUID,pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<LoginSession>
}
