// AC-01..06 (T4.2): BoxTemplateController responsibility.
package com.foodrescue.backend.surprisebox.controller

import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.surprisebox.dto.*
import com.foodrescue.backend.surprisebox.service.BoxTemplateService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}/box-templates")
class BoxTemplateController(private val templates: BoxTemplateService, private val current: CurrentAccount) {
    @GetMapping
    fun list(@PathVariable storeId: UUID, @RequestParam(required = false) active: Boolean?): List<BoxTemplateView> =
        templates.list(current.get().accountId, storeId, active)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable storeId: UUID, @Valid @RequestBody r: BoxTemplateCreateRequest): BoxTemplateView =
        templates.create(current.get().accountId, storeId, r)

    @GetMapping("/{templateId}")
    fun get(@PathVariable storeId: UUID, @PathVariable templateId: UUID): BoxTemplateView = templates.get(current.get().accountId, storeId, templateId)

    @PatchMapping("/{templateId}")
    fun update(@PathVariable storeId: UUID, @PathVariable templateId: UUID, @Valid @RequestBody r: BoxTemplateUpdateRequest): BoxTemplateView =
        templates.update(current.get().accountId, storeId, templateId, r)

    @PostMapping("/{templateId}/status")
    fun status(@PathVariable storeId: UUID, @PathVariable templateId: UUID, @Valid @RequestBody r: BoxTemplateStatusRequest): BoxTemplateView =
        templates.changeStatus(current.get().accountId, storeId, templateId, r)
}
