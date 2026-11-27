package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.SettlementLine
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SettlementLineRepository : JpaRepository<SettlementLine, UUID> {
    fun findAllBySettlementIdOrderByCreatedAtAscIdAsc(settlementId: UUID): List<SettlementLine>
    fun findAllByLedgerEntryIdIn(ledgerEntryIds: Collection<UUID>): List<SettlementLine>
}
