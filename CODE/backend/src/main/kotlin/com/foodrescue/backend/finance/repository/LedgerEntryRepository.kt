package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.LedgerEntry
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LedgerEntryRepository : JpaRepository<LedgerEntry, UUID> {
    fun existsByBusinessKey(businessKey: String): Boolean
    fun findByBusinessKey(businessKey: String): LedgerEntry?
    fun findAllByEventOrderIdOrderByOccurredAtAscIdAsc(orderId: UUID): List<LedgerEntry>
    fun findAllByEventOrderIdAndTypeOrderByOccurredAtAscIdAsc(orderId: UUID, type: com.foodrescue.backend.finance.LedgerEntryType): List<LedgerEntry>
}
