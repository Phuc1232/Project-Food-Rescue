// AC-03,09 (T3.4): MyStaffAssignmentController responsibility.
package com.foodrescue.backend.staff.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.staff.dto.MyAssignmentView
import com.foodrescue.backend.staff.service.StaffSelfService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/staff/assignments")
class MyStaffAssignmentController(private val self: StaffSelfService, private val current: CurrentAccount) {
    @GetMapping
    fun mine(): List<MyAssignmentView> = self.mine(current.get().accountId)

    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: UUID): MyAssignmentView = self.respond(current.get().accountId, id, accept = true)

    @PostMapping("/{id}/decline")
    fun decline(@PathVariable id: UUID): MyAssignmentView = self.respond(current.get().accountId, id, accept = false)
}
