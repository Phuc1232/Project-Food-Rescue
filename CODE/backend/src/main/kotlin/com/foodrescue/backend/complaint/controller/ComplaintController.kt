// AC-01,02,03,05,11: Customer complaint endpoints.
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
@RequestMapping("/api/v1/complaints")
@PreAuthorize("hasRole('CUSTOMER')")
class ComplaintController(private val service: ComplaintService,private val current: CurrentAccount) {
    @PostMapping
    fun submit(@Valid @RequestBody r: ComplaintSubmitRequest,@RequestHeader("Idempotency-Key",required=false) key: String?): ResponseEntity<CustomerComplaintDetail> {
        val result=service.submit(current.get().accountId,r,key)
        return ResponseEntity.status(if(result.created) 201 else 200).body(result.view)
    }
    @GetMapping
    fun list(@RequestParam(required=false) status: ComplaintStatus?,@RequestParam(required=false) orderId: UUID?,
        @RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int)=service.myList(current.get().accountId,status,orderId,page,size)
    @GetMapping("/{complaintId}")
    fun detail(@PathVariable complaintId: UUID)=service.myDetail(current.get().accountId,complaintId)
    @PostMapping("/{complaintId}/supplement")
    fun supplement(@PathVariable complaintId: UUID,@Valid @RequestBody r: ComplaintSupplementRequest)=service.supplement(current.get().accountId,complaintId,r)
}
