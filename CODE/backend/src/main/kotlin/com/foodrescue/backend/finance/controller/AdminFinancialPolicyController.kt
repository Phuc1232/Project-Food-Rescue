// AC-01..06 (T4.5): AdminFinancialPolicyController responsibility.
package com.foodrescue.backend.finance.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.finance.dto.FinancialPolicyRequest
import com.foodrescue.backend.finance.dto.FinancialPolicyView
import com.foodrescue.backend.finance.service.FinancialPolicyService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/financial-policies")
@PreAuthorize("hasRole('ADMIN')")
class AdminFinancialPolicyController(private val policies: FinancialPolicyService, private val current: CurrentAccount) {
    @GetMapping
    fun all(): List<FinancialPolicyView> = policies.all()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody r: FinancialPolicyRequest): FinancialPolicyView = policies.create(current.get().accountId, r)
}
