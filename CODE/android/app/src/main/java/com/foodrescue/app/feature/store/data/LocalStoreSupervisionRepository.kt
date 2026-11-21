package com.foodrescue.app.feature.store.data

import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.menu.data.LocalMenuRepository
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.store.domain.*
import com.foodrescue.app.feature.surprisebox.data.LocalPackedBoxStore
import com.foodrescue.app.feature.surprisebox.data.OwnerBoxJson
import com.foodrescue.app.feature.surprisebox.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal class LocalStoreSupervisionRepository(context: Context, email: String,
    private val clock: () -> Long = System::currentTimeMillis) : StoreSupervisionRepository {
    private val context = context.applicationContext
    private val actor = email.trim().lowercase(Locale.ROOT)
    private val records = LocalStoreRecords(context)
    private val directory = LocalPublicStoreRepository(context)
    private val orders = LocalOrderLedger(context)
    private val packed = LocalPackedBoxStore(context)

    override suspend fun stores(): List<SupervisedStore> = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        synchronized(LocalCommerceLock) {
            directory.registeredAccounts().mapNotNull { (key, id) ->
                records.read(key)?.takeIf { it.status == StoreReviewStatus.APPROVED }?.let { SupervisedStore(key, id, it) }
            }.sortedWith(compareByDescending<SupervisedStore> { it.store.governance.suspension != null }
                .thenBy { it.store.profile.name })
        }
    }

    override suspend fun detail(accountKey: String): StoreSupervisionDetail = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        synchronized(LocalCommerceLock) { entry(accountKey) }
        // Never acquire the menu mutex while holding commerce: staff locks menu -> catalog -> commerce.
        val menu = LocalMenuRepository.readForAccount(context, accountKey)
        synchronized(LocalCommerceLock) {
            val current = entry(accountKey)
            val now = clock()
            val boxes = packed.load(current.storeId).sortedByDescending { it.packedAt }
            val storeOrders = orders.read().map { it.order }.filter { it.boxSnapshot?.storeId == current.storeId }
            val catalog = catalog(accountKey).let { catalog -> catalog.copy(batches = catalog.batches.map { batch ->
                val stock = orders.stock(current.storeId, batch.id, now)
                batch.copy(held = stock.held, sold = stock.sold)
            }) }
            val activity = buildList {
                current.store.reviewHistory.forEachIndexed { i, event -> add(StoreActivity("review:$i", event.atMillis,
                    when (event.action) {
                        StoreReviewAction.SUBMITTED -> "Gửi hồ sơ cửa hàng"
                        StoreReviewAction.APPROVED -> "Duyệt hồ sơ cửa hàng"
                        StoreReviewAction.CHANGES_REQUESTED -> "Yêu cầu bổ sung hồ sơ"
                    }, listOf(event.actor, event.note).filter(String::isNotBlank).joinToString(" · "))) }
                current.store.governance.events.forEach { event -> add(StoreActivity("restriction:${event.id}", event.atMillis,
                    when (event.action) {
                        StoreRestrictionAction.SUSPEND_STORE -> "Tạm ngừng bán toàn cửa hàng"
                        StoreRestrictionAction.RESTORE_STORE -> "Khôi phục quyền bán của cửa hàng"
                        StoreRestrictionAction.STOP_BATCH -> "Ngừng bán đợt vi phạm"
                        StoreRestrictionAction.RELEASE_BATCH -> "Gỡ hạn chế đợt bán"
                    }, listOfNotNull(event.batchName, event.note, event.actor).joinToString(" · "))) }
                menu.items.forEach { add(StoreActivity("menu:${it.id}", it.updatedAtMillis,
                    "Cập nhật menu gần nhất", "${it.name} · ${it.price} đ")) }
                boxes.forEach { box ->
                    add(StoreActivity("packed:${box.id}", box.packedAt, "Đóng gói ${box.code}", "${box.templateName} · ${box.preparedByName}"))
                    box.inspections.forEach { add(StoreActivity("inspection:${it.id}", it.atMillis,
                        if (it.result == InspectionResult.PASSED) "Kiểm tra hộp đạt yêu cầu" else "Loại hộp không đạt",
                        "${box.code} · ${it.employeeName} · ${it.note}")) }
                }
                storeOrders.forEach { order ->
                    add(StoreActivity("order:${order.id}", order.createdAtMillis, "Đặt hộp", order.reference))
                    order.handover?.let { add(StoreActivity("handover:${order.id}", it.atMillis, "Bàn giao hoàn tất", order.reference)) }
                    order.noShow?.let { add(StoreActivity("noshow:${order.id}", it.atMillis, "Xác nhận khách không đến", order.reference)) }
                    order.cancelledAtMillis?.let { add(StoreActivity("cancel:${order.id}", it, "Hủy đơn hàng", order.reference)) }
                }
            }.sortedWith(compareByDescending<StoreActivity> { it.atMillis }.thenBy { it.id })
            StoreSupervisionDetail(current, menu, catalog, boxes, activity,
                storeOrders.count { it.isPaid && it.cancelledAtMillis == null && it.collectedAtMillis == null && it.noShow == null },
                storeOrders.count { it.isActive(now) }, now)
        }
    }

    override suspend fun decide(accountKey: String, draft: StoreRestrictionDraft): SupervisedStore = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        synchronized(LocalCommerceLock) {
            val before = entry(accountKey)
            draft.batch?.let { expected ->
                val current = catalog(accountKey).batches.find { it.id == expected.id } ?: throw StaleStoreSupervision()
                if (current.template != expected.template || current.toDraft() != expected.toDraft() || current.control != expected.control)
                    throw StaleStoreSupervision()
            }
            val store = records.change(accountKey, { throw StaleStoreSupervision() }) { it.restrict(draft, actor, clock()) }
            before.copy(store = store)
        }
    }

    private fun entry(key: String): SupervisedStore {
        val id = directory.registeredAccounts()[key] ?: throw StaleStoreSupervision()
        val store = records.read(key)?.takeIf { it.status == StoreReviewStatus.APPROVED } ?: throw StaleStoreSupervision()
        return SupervisedStore(key, id, store)
    }
    private fun catalog(key: String): OwnerBoxCatalog = context.getSharedPreferences("owner_boxes_$key", Context.MODE_PRIVATE)
        .getString("catalog.v1", null)?.let(OwnerBoxJson::decode) ?: OwnerBoxCatalog()
}
