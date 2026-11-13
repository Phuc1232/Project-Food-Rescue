// AC-01,02,03,04,13: PartnerApplicationController responsibility.
package com.foodrescue.backend.store.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.store.dto.MyPartnerApplicationResponse
import com.foodrescue.backend.store.dto.PartnerApplicationRequest
import com.foodrescue.backend.store.service.PartnerApplicationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/partner-applications")
class PartnerApplicationController(private val applications: PartnerApplicationService, private val current: CurrentAccount) {
    /** 201 for a new revision; 200 when a revision is already awaiting review (returned unchanged). */
    @PostMapping
    fun submit(@Valid @RequestBody request: PartnerApplicationRequest): ResponseEntity<MyPartnerApplicationResponse> {
        val result = applications.submit(current.get().accountId, request)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK).body(result.application)
    }

    @GetMapping("/me")
    fun mine(): MyPartnerApplicationResponse = applications.mine(current.get().accountId)
}
