// AC-07,08 (T4.4): BoxQueryService responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.surprisebox.BoxStatus
import com.foodrescue.backend.surprisebox.dto.BoxDetail
import com.foodrescue.backend.surprisebox.dto.BoxView
import com.foodrescue.backend.surprisebox.dto.PrefillItemView
import com.foodrescue.backend.surprisebox.repository.BoxItemRepository
import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import com.foodrescue.backend.surprisebox.repository.SaleBatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** UC-32, UC-33 and the quick-fill source (UC-26 step 2-3). */
@Service
class BoxQueryService(
    private val access: BoxAccess,
    private val batches: SaleBatchRepository,
    private val boxes: PhysicalBoxRepository,
    private val items: BoxItemRepository,
    private val views: BoxViews
) {
    @Transactional(readOnly = true)
    fun list(accountId: UUID, storeId: UUID, batchId: UUID, status: BoxStatus?): List<BoxView> {
        access.requireRead(accountId, storeId)
        batches.findById(batchId).orElse(null)?.takeIf { it.store.id == storeId } ?: notFound()
        return boxes.findAllByCurrentBatchIdOrderByPackedAtDesc(batchId).filter { status == null || it.status == status }.map(views::box)
    }

    @Transactional(readOnly = true)
    fun detail(accountId: UUID, storeId: UUID, boxId: UUID): BoxDetail {
        access.requireRead(accountId, storeId)
        return views.detail(boxes.findById(boxId).orElse(null)?.takeIf { it.store.id == storeId } ?: notFound())
    }

    /** Items and quantities only, priced and flagged from the current menu; the packer adjusts them (BR-12). */
    @Transactional(readOnly = true)
    fun prefill(accountId: UUID, storeId: UUID, boxId: UUID): List<PrefillItemView> {
        access.requirePack(accountId, storeId)
        val box = boxes.findById(boxId).orElse(null)?.takeIf { it.store.id == storeId } ?: notFound()
        return items.findAllByBoxIdOrderByItemNameSnapshot(box.id!!).map {
            PrefillItemView(it.menuItem.id!!, it.menuItem.name, it.quantity, it.menuItem.listedPrice.setScale(0), it.menuItem.status.name)
        }
    }

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")
}
