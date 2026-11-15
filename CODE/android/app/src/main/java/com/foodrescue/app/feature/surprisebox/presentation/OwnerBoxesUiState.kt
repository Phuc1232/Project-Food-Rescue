package com.foodrescue.app.feature.surprisebox.presentation

import com.foodrescue.app.feature.surprisebox.domain.*

internal enum class OwnerBoxPage { LIST, TEMPLATE_EDIT, VALUE_CALCULATOR, BATCH_EDIT, BATCH_DETAIL }
internal enum class OwnerBoxSection { BATCHES, TEMPLATES }
internal data class OwnerBoxesUiState(
    val catalog: OwnerBoxCatalog = OwnerBoxCatalog(), val loading: Boolean = true, val busy: Boolean = false,
    val page: OwnerBoxPage = OwnerBoxPage.LIST, val section: OwnerBoxSection = OwnerBoxSection.BATCHES,
    val selectedId: String? = null, val templateDraft: BoxTemplateDraft = BoxTemplateDraft(),
    val batchDraft: SaleBatchDraft = SaleBatchDraft(), val initialBatchDraft: SaleBatchDraft = SaleBatchDraft(),
    val query: String = "", val filter: BatchStatus? = null, val now: Long = System.currentTimeMillis(),
    val fields: Set<BoxField> = emptySet(), val error: String? = null, val message: String? = null,
    val confirmDiscard: Boolean = false, val pendingAction: BatchAction? = null, val toggleTemplateId: String? = null,
    val calculator: BoxValueCalculatorState = BoxValueCalculatorState(),
) {
    val selected: SaleBatch? get() = catalog.batches.firstOrNull { it.id == selectedId }
    val editedBatch: SaleBatch? get() = catalog.batches.firstOrNull { it.id == batchDraft.id }
    val batchTemplate: BoxTemplate? get() = editedBatch?.template?.takeIf { it.id == batchDraft.templateId }
        ?: catalog.templates.firstOrNull { it.id == batchDraft.templateId }
    val dirty: Boolean get() = when (page) {
        OwnerBoxPage.TEMPLATE_EDIT -> templateDraft != (catalog.templates.firstOrNull { it.id == templateDraft.id }?.toDraft() ?: BoxTemplateDraft())
        OwnerBoxPage.BATCH_EDIT -> batchDraft != initialBatchDraft
        else -> false
    }
    val visibleTemplates: List<BoxTemplate> get() = catalog.templates.filter { it.name.boxSearchKey().contains(query.boxSearchKey()) }.asReversed()
    val visibleBatches: List<SaleBatch> get() = catalog.batches.filter {
        (filter == null || it.statusAt(now) == filter) && it.template.name.boxSearchKey().contains(query.boxSearchKey())
    }.sortedByDescending { it.salesStart }
    val liveBatches: List<SaleBatch> get() = catalog.batches.filter { it.statusAt(now) == BatchStatus.SELLING }
    val available: Int get() = liveBatches.sumOf { it.remaining }
}

internal val BatchStatus.label: String get() = when (this) {
    BatchStatus.DRAFT -> "Bản nháp"; BatchStatus.SCHEDULED -> "Đã lên lịch"; BatchStatus.SELLING -> "Đang bán"
    BatchStatus.PAUSED -> "Tạm dừng"; BatchStatus.SOLD_OUT -> "Hết hộp"; BatchStatus.ENDED -> "Đã kết thúc"
}
internal val BatchAction.label: String get() = when (this) {
    BatchAction.PUBLISH -> "Mở bán"; BatchAction.PAUSE -> "Tạm dừng"; BatchAction.RESUME -> "Tiếp tục bán"; BatchAction.END -> "Kết thúc đợt bán"
}
internal val BoxField.message: String get() = when (this) {
    BoxField.NAME -> "Nhập tên hộp từ 2 đến 80 ký tự."
    BoxField.DUPLICATE -> "Tên mẫu hộp này đã tồn tại."
    BoxField.DESCRIPTION -> "Nhập mô tả từ 10 đến 500 ký tự."
    BoxField.ALLERGENS -> "Nhập thành phần gây dị ứng hoặc ghi rõ nếu chưa xác định (2–250 ký tự)."
    BoxField.PRICE -> "Nhập giá nguyên đồng lớn hơn 0 và thấp hơn giá trị tối thiểu."
    BoxField.VALUE -> "Nhập giá trị từ 2 đến 999.999.999 đ."
    BoxField.TEMPLATE -> "Chọn một mẫu hộp đang sử dụng."
    BoxField.QUANTITY -> "Số lượng từ 1 đến 9.999 hộp, không thấp hơn tổng đang giữ và đã bán."
    BoxField.SALES -> "Giờ đóng bán phải sau giờ mở bán và thời điểm hiện tại."
    BoxField.PICKUP -> "Giờ nhận phải từ lúc đóng bán; giờ kết thúc nhận phải sau giờ bắt đầu."
    BoxField.LOCKED -> "Đợt bán đã đổi trạng thái hoặc có đơn; chỉ cập nhật thông tin còn được phép."
}
