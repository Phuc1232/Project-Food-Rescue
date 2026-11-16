// AC-01..07 (T3.5): AdminAccountController responsibility.
package com.foodrescue.backend.account.controller

import com.foodrescue.backend.account.AccountStatus
import com.foodrescue.backend.account.PlatformRole
import com.foodrescue.backend.account.dto.AccountStatusRequest
import com.foodrescue.backend.account.dto.AdminAccountDetail
import com.foodrescue.backend.account.dto.AdminAccountSummary
import com.foodrescue.backend.account.service.AdminAccountQueryService
import com.foodrescue.backend.account.service.AdminAccountStatusService
import com.foodrescue.backend.common.response.PageResponse
import com.foodrescue.backend.common.security.CurrentAccount
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
class AdminAccountController(
    private val queries: AdminAccountQueryService,
    private val statuses: AdminAccountStatusService,
    private val current: CurrentAccount
) {
    @GetMapping
    fun search(@RequestParam(required = false) q: String?, @RequestParam(required = false) role: PlatformRole?,
               @RequestParam(required = false) status: AccountStatus?, @RequestParam(defaultValue = "0") page: Int,
               @RequestParam(defaultValue = "20") size: Int): PageResponse<AdminAccountSummary> = queries.search(q, role, status, page, size)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: UUID): AdminAccountDetail = queries.detail(id)

    @PostMapping("/{id}/status")
    fun changeStatus(@PathVariable id: UUID, @Valid @RequestBody request: AccountStatusRequest): AdminAccountDetail =
        statuses.change(current.get().accountId, id, request)
}
