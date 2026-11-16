// AC-01..09 (T4.1): MenuItemController responsibility.
package com.foodrescue.backend.catalog.controller

import com.foodrescue.backend.catalog.MenuItemStatus
import com.foodrescue.backend.catalog.dto.*
import com.foodrescue.backend.catalog.service.MenuItemService
import com.foodrescue.backend.catalog.service.MenuQueryService
import com.foodrescue.backend.catalog.service.PriceDocumentService
import com.foodrescue.backend.common.security.CurrentAccount
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stores/{storeId}")
class MenuItemController(
    private val queries: MenuQueryService,
    private val items: MenuItemService,
    private val documents: PriceDocumentService,
    private val current: CurrentAccount
) {
    @GetMapping("/menu-items")
    fun list(@PathVariable storeId: UUID, @RequestParam(required = false) status: MenuItemStatus?,
             @RequestParam(required = false) categoryCode: String?): List<MenuItemView> = queries.list(current.get().accountId, storeId, status, categoryCode)

    @PostMapping("/menu-items")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable storeId: UUID, @Valid @RequestBody r: MenuItemCreateRequest): MenuItemDetail = items.create(current.get().accountId, storeId, r)

    @GetMapping("/menu-items/{itemId}")
    fun detail(@PathVariable storeId: UUID, @PathVariable itemId: UUID): MenuItemDetail = queries.detail(current.get().accountId, storeId, itemId)

    @PatchMapping("/menu-items/{itemId}")
    fun update(@PathVariable storeId: UUID, @PathVariable itemId: UUID, @Valid @RequestBody r: MenuItemUpdateRequest): MenuItemDetail =
        items.update(current.get().accountId, storeId, itemId, r)

    @PostMapping("/menu-items/{itemId}/status")
    fun status(@PathVariable storeId: UUID, @PathVariable itemId: UUID, @Valid @RequestBody r: MenuItemStatusRequest): MenuItemDetail =
        items.changeStatus(current.get().accountId, storeId, itemId, r)

    @GetMapping("/price-documents")
    fun documents(@PathVariable storeId: UUID): List<PriceDocumentView> = documents.list(current.get().accountId, storeId)

    @PostMapping("/price-documents")
    @ResponseStatus(HttpStatus.CREATED)
    fun addDocument(@PathVariable storeId: UUID, @Valid @RequestBody r: PriceDocumentRequest): PriceDocumentView =
        documents.add(current.get().accountId, storeId, r)
}
