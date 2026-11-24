// AC-04..11: Admin complaint endpoints.
package com.foodrescue.backend.complaint.controller
import com.foodrescue.backend.complaint.*
import com.foodrescue.backend.complaint.dto.*
import com.foodrescue.backend.complaint.service.*
import com.foodrescue.backend.common.security.CurrentAccount
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import java.util.UUID
import java.time.Instant

@RestController
@RequestMapping("/api/v1/admin/complaints")
@PreAuthorize("hasRole('ADMIN')")
class AdminComplaintController(private val service: AdminComplaintService,private val current: CurrentAccount) {
    @GetMapping
    fun list(@RequestParam(required=false) status: ComplaintStatus?,@RequestParam(required=false) type: ComplaintType?,
        @RequestParam(required=false) storeId: UUID?,@RequestParam(required=false) orderId: UUID?,
        @RequestParam(required=false) from: Instant?,@RequestParam(required=false) to: Instant?,
        @RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int)=service.list(status,type,storeId,orderId,from,to,page,size)
    @GetMapping("/{complaintId}")
    fun detail(@PathVariable complaintId: UUID)=service.detail(complaintId)
    @PostMapping("/{complaintId}/request-info")
    fun requestInfo(@PathVariable complaintId: UUID,@Valid @RequestBody r: ComplaintRequestInfoRequest)=service.requestInfo(current.get().accountId,complaintId,r)
    @PostMapping("/{complaintId}/decide")
    fun decide(@PathVariable complaintId: UUID,@Valid @RequestBody r: ComplaintDecisionRequest,@RequestHeader("Idempotency-Key",required=false) key: String?)=service.decide(current.get().accountId,complaintId,r,key)
    @PostMapping("/{complaintId}/close")
    fun close(@PathVariable complaintId: UUID)=service.close(current.get().accountId,complaintId)
}
