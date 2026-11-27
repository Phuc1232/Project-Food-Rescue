package com.foodrescue.backend.finance.repository

import com.foodrescue.backend.finance.entity.FinancialEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FinancialEventRepository : JpaRepository<FinancialEvent, UUID> {
    fun findByBusinessKey(businessKey: String): FinancialEvent?
    fun findAllByOrderIdOrderByOccurredAtAscIdAsc(orderId: UUID): List<FinancialEvent>
    fun findFirstByOrderIdAndType(orderId: UUID, type: com.foodrescue.backend.finance.FinancialEventType): FinancialEvent?
}
