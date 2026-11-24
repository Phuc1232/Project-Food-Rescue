// AC-02,04 (T5.2): BoxAllocationRepository responsibility.
package com.foodrescue.backend.order.repository

import com.foodrescue.backend.order.entity.BoxAllocation
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface BoxAllocationRepository : JpaRepository<BoxAllocation, UUID> {
    fun existsByBoxIdAndStatusIn(boxId: UUID, statuses: Collection<com.foodrescue.backend.order.AllocationStatus>): Boolean
    fun findAllByBoxIdOrderByReservedAtDesc(boxId: UUID): List<BoxAllocation>
    fun findAllByOrderIdOrderByReservedAt(orderId: UUID): List<BoxAllocation>
    fun findAllByOrderIdAndStatus(orderId: UUID, status: com.foodrescue.backend.order.AllocationStatus): List<BoxAllocation>
}
