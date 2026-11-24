package com.foodrescue.backend.complaint.repository

import com.foodrescue.backend.complaint.entity.Complaint
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ComplaintRepository : JpaRepository<Complaint, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Complaint> {
    fun existsByComplaintCode(code: String): Boolean
    fun findByCustomerIdAndIdempotencyKeyAndSourceBoxIsNull(customerId: UUID, key: String): Complaint?
    fun existsByOrderIdAndTypeAndStatusIn(orderId: UUID, type: com.foodrescue.backend.complaint.ComplaintType, statuses: Collection<com.foodrescue.backend.complaint.ComplaintStatus>): Boolean
    fun existsByOrderIdAndIdNotAndStatusIn(orderId: UUID, id: UUID, statuses: Collection<com.foodrescue.backend.complaint.ComplaintStatus>): Boolean
    @org.springframework.data.jpa.repository.Query("select c.order.id from Complaint c where c.id = :id")
    fun findOrderId(id: UUID): UUID?
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Complaint c where c.id = :id")
    fun findForUpdate(id: UUID): Complaint?

    fun findFirstBySourceBoxIdAndStatusInOrderByCreatedAtDesc(boxId: UUID, statuses: Collection<com.foodrescue.backend.complaint.ComplaintStatus>): Complaint?
    fun findBySourceBoxIdAndIdempotencyKey(boxId: UUID, key: String): Complaint?
}
