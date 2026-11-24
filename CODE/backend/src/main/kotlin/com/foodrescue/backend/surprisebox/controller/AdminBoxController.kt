// AC-01..12 (T7.5): AdminBoxController responsibility.
package com.foodrescue.backend.surprisebox.controller
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.surprisebox.dto.BoxUnfitRequest
import com.foodrescue.backend.surprisebox.service.BoxUnfitService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID
@RestController
@RequestMapping("/api/v1/admin/boxes")
@PreAuthorize("hasRole('ADMIN')")
class AdminBoxController(private val service: BoxUnfitService, private val current: CurrentAccount) {
    @PostMapping("/{boxId}/block")
    fun block(@PathVariable boxId: UUID, @Valid @RequestBody r: BoxUnfitRequest,
              @RequestHeader("Idempotency-Key", required = false) key: String?) = service.blockBoxByAdmin(current.get().accountId, boxId, r, key)
}
