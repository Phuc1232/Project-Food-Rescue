// AC-01 (T4.5): FinancialPolicyController responsibility.
package com.foodrescue.backend.finance.controller

import com.foodrescue.backend.finance.dto.FinancialPolicyView
import com.foodrescue.backend.finance.service.FinancialPolicyService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** Owners see the rates in force (BR-46); customers see no-show retention and terms before checkout (UC-85 step 6). */
@RestController
class FinancialPolicyController(private val policies: FinancialPolicyService) {
    @GetMapping("/api/v1/financial-policies/current")
    fun current(): FinancialPolicyView = policies.current()
}
