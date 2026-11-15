// AC-01..08 (T3.4): StoreStaffController responsibility.
package com.foodrescue.backend.staff.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.staff.AssignmentStatus
import com.foodrescue.backend.staff.dto.StaffInviteRequest
import com.foodrescue.backend.staff.dto.StaffMemberView
import com.foodrescue.backend.staff.dto.StaffPermissionsRequest
import com.foodrescue.backend.staff.dto.StaffRevokeRequest
import com.foodrescue.backend.staff.service.StaffManagementService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}/staff")
class StoreStaffController(private val staff: StaffManagementService, private val current: CurrentAccount) {
    @GetMapping
    fun list(@PathVariable storeId: UUID, @RequestParam(required = false) status: AssignmentStatus?): List<StaffMemberView> =
        staff.list(current.get().accountId, storeId, status)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun invite(@PathVariable storeId: UUID, @Valid @RequestBody request: StaffInviteRequest): StaffMemberView =
        staff.invite(current.get().accountId, storeId, request)

    @PutMapping("/{assignmentId}/permissions")
    fun permissions(@PathVariable storeId: UUID, @PathVariable assignmentId: UUID,
                    @Valid @RequestBody request: StaffPermissionsRequest): StaffMemberView =
        staff.changePermissions(current.get().accountId, storeId, assignmentId, request.permissions!!)

    @PostMapping("/{assignmentId}/revoke")
    fun revoke(@PathVariable storeId: UUID, @PathVariable assignmentId: UUID, @Valid @RequestBody request: StaffRevokeRequest): StaffMemberView =
        staff.revoke(current.get().accountId, storeId, assignmentId, request.reason!!)
}
