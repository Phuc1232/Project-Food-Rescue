// AC-01..14 (T7.4): StoreNoShowController responsibility.
package com.foodrescue.backend.pickup.controller
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.order.OrderStatus
import com.foodrescue.backend.pickup.dto.NoShowConfirmRequest
import com.foodrescue.backend.pickup.service.NoShowService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID
@RestController
@RequestMapping("/api/v1/stores/{storeId}")
class StoreNoShowController(private val service: NoShowService, private val current: CurrentAccount) {
    @GetMapping("/no-shows/overdue")
    fun overdue(@PathVariable storeId: UUID, @RequestParam(required = false) date: LocalDate?, @RequestParam(required = false) status: OrderStatus?) =
        service.overdueOrders(current.get().accountId, storeId, date, status)
    @GetMapping("/no-shows/{caseId}")
    fun detail(@PathVariable storeId: UUID, @PathVariable caseId: UUID) = service.detail(current.get().accountId, storeId, caseId)
    @PostMapping("/orders/{orderId}/no-show")
    fun confirm(@PathVariable storeId: UUID, @PathVariable orderId: UUID, @Valid @RequestBody r: NoShowConfirmRequest,
                @RequestHeader("Idempotency-Key", required = false) key: String?) =
        service.confirm(current.get().accountId, storeId, orderId, r, key).let { ResponseEntity.status(if (it.created) 201 else 200).body(it.view) }
}
