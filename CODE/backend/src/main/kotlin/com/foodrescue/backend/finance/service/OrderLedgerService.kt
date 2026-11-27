// AC-01..10 (T8.3): OrderLedgerService responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.LedgerEntryType
import com.foodrescue.backend.finance.dto.*
import com.foodrescue.backend.finance.repository.LedgerEntryRepository
import com.foodrescue.backend.order.repository.CustomerOrderRepository
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID
@Service
class OrderLedgerService(private val ledger: LedgerEntryRepository, private val orders: CustomerOrderRepository) {
    @Transactional(readOnly=true)
    fun view(orderId: UUID): OrderLedgerView {
        val o=orders.findById(orderId).orElse(null) ?: throw ApiException(404,"NOT_FOUND","Order not found")
        val entries=ledger.findAllByEventOrderIdOrderByOccurredAtAscIdAsc(orderId)
        fun gross(type: LedgerEntryType)=entries.filter { it.type==type }.fold(BigDecimal.ZERO) { s,e -> s+e.amount }.setScale(0)
        fun adjustment(type: LedgerEntryType)=entries.filter { it.type==LedgerEntryType.ADJUSTMENT && it.originalEntry?.type==type }.fold(BigDecimal.ZERO) { s,e -> s+e.amount }.setScale(0)
        val collected=gross(LedgerEntryType.PAYMENT_RECEIPT); val refunded=gross(LedgerEntryType.CUSTOMER_REFUND).abs()
        val commission=gross(LedgerEntryType.PLATFORM_COMMISSION);val commAdj=adjustment(LedgerEntryType.PLATFORM_COMMISSION)
        val payable=gross(LedgerEntryType.STORE_PAYABLE);val storeAdj=adjustment(LedgerEntryType.STORE_PAYABLE)
        val noShow=gross(LedgerEntryType.NO_SHOW_COMPENSATION);val noShowAdj=adjustment(LedgerEntryType.NO_SHOW_COMPENSATION)
        val allocated=commission+commAdj+payable+storeAdj+noShow+noShowAdj
        return OrderLedgerView(orderId,o.orderCode,collected,refunded,collected-refunded,commission,commAdj,commission+commAdj,
            payable,storeAdj,payable+storeAdj,noShow,noShowAdj,noShow+noShowAdj,allocated,allocated.compareTo(collected-refunded)==0,
            entries.map { LedgerEntryView(it.id!!,it.event.id!!,it.event.type.name,it.type.name,it.amount.setScale(0),it.currency,it.originalEntry?.id,it.businessKey,it.occurredAt) })
    }
}
