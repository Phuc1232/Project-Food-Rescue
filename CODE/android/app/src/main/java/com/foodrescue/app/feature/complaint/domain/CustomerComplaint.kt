package com.foodrescue.app.feature.complaint.domain

internal const val MAX_COMPLAINT_IMAGES = 3
internal const val MAX_EVIDENCE_BYTES = 5L * 1024 * 1024
internal const val MIN_COMPLAINT_LENGTH = 20
internal const val MAX_COMPLAINT_LENGTH = 2000

internal enum class ComplaintReason { FOOD_QUALITY, MISSING_ITEMS, STORE_UNAVAILABLE, PICKUP_ISSUE, PAYMENT_REFUND, OTHER }
internal enum class ComplaintStatus { PENDING, IN_REVIEW, RESOLVED, REJECTED;
    val isOpen: Boolean get() = this == PENDING || this == IN_REVIEW
}
internal enum class ComplaintFilter { ALL, OPEN, CLOSED }
internal enum class ComplaintIssue {
    ORDER_REQUIRED, ORDER_UNAVAILABLE, REASON_REQUIRED, DESCRIPTION_TOO_SHORT, DESCRIPTION_TOO_LONG,
    TOO_MANY_ATTACHMENTS, OPEN_CASE_EXISTS, NETWORK_UNAVAILABLE, ATTACHMENT_UNAVAILABLE,
}

internal data class ComplaintOrder(val id: String, val reference: String, val store: String,
    val boxName: String, val quantity: Int, val amount: Long, val createdAtMillis: Long)

internal data class ComplaintEvidence(val id: String, val name: String, val mimeType: String,
    val sizeBytes: Long, val previewUri: String)

internal data class ComplaintDraft(val id: String, val orderId: String? = null, val reason: ComplaintReason? = null,
    val description: String = "", val evidence: List<ComplaintEvidence> = emptyList()) {
    val hasContent: Boolean get() = reason != null || description.isNotBlank() || evidence.isNotEmpty()
}

internal data class CustomerComplaint(
    val id: String,
    val order: ComplaintOrder,
    val reason: ComplaintReason,
    val description: String,
    val evidence: List<ComplaintEvidence>,
    val status: ComplaintStatus,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val response: String? = null,
) {
    init {
        require(id.isNotBlank() && order.id.isNotBlank())
        require(description.trim().length in MIN_COMPLAINT_LENGTH..MAX_COMPLAINT_LENGTH)
        require(evidence.size <= MAX_COMPLAINT_IMAGES && evidence.distinctBy { it.id }.size == evidence.size)
        require(updatedAtMillis >= createdAtMillis)
        require(status.isOpen || !response.isNullOrBlank())
    }
}

internal fun complaintIssue(draft: ComplaintDraft, orders: List<ComplaintOrder>, cases: List<CustomerComplaint>): ComplaintIssue? = when {
    draft.orderId == null -> ComplaintIssue.ORDER_REQUIRED
    orders.none { it.id == draft.orderId } -> ComplaintIssue.ORDER_UNAVAILABLE
    cases.any { it.order.id == draft.orderId && it.status.isOpen } -> ComplaintIssue.OPEN_CASE_EXISTS
    draft.reason == null -> ComplaintIssue.REASON_REQUIRED
    draft.description.trim().length < MIN_COMPLAINT_LENGTH -> ComplaintIssue.DESCRIPTION_TOO_SHORT
    draft.description.length > MAX_COMPLAINT_LENGTH -> ComplaintIssue.DESCRIPTION_TOO_LONG
    draft.evidence.size > MAX_COMPLAINT_IMAGES -> ComplaintIssue.TOO_MANY_ATTACHMENTS
    else -> null
}
