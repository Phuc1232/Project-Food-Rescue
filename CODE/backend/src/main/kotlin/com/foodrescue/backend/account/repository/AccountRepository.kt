// AC-05,14,21,22: persistence support for verified accounts and controlled sessions.
package com.foodrescue.backend.account.repository

import com.foodrescue.backend.account.entity.Account
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AccountRepository : JpaRepository<Account, UUID> {
    fun findByLoginIdentifier(loginIdentifier: String): Account?
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.loginIdentifier = :email")
    fun findEmailForUpdate(@Param("email") email: String): Account?

    /** Must be called inside the same transaction as the dependent validation and writes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from Account entity where entity.id = :id")
    fun findForUpdate(@Param("id") id: UUID): Account?
}
