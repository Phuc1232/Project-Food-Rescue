// AC-01..07 (T8.5): AdminMenuAuditController responsibility.
package com.foodrescue.backend.catalog.controller
import com.foodrescue.backend.catalog.service.MenuPriceInspectionService
import com.foodrescue.backend.catalog.MenuItemStatus
import org.springframework.web.bind.annotation.*
import org.springframework.security.access.prepost.PreAuthorize
import java.util.UUID
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/stores/{storeId}/menu-price-audit")
class AdminMenuAuditController(private val service: MenuPriceInspectionService) {
    @GetMapping fun inspect(@PathVariable storeId: UUID,@RequestParam(required=false) menuItemId: UUID?,@RequestParam(required=false) status: MenuItemStatus?,
        @RequestParam(defaultValue="false") onlyMismatchedWithPackedBoxes: Boolean,@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int)=service.inspect(storeId,menuItemId,status,onlyMismatchedWithPackedBoxes,page,size)
}
