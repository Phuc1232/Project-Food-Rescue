package com.foodrescue.app.feature.complaint.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodrescue.app.feature.complaint.domain.*
import kotlinx.coroutines.*
import java.util.Locale
import java.util.UUID

internal data class ComplaintUiState(
    val cases: List<CustomerComplaint>,
    val draft: ComplaintDraft,
    val filter: ComplaintFilter = ComplaintFilter.ALL,
    val issue: ComplaintIssue? = null,
    val evidenceIssue: EvidenceIssue? = null,
    val importing: Boolean = false,
    val pendingOrderId: String? = null,
) {
    val visibleCases: List<CustomerComplaint> get() = cases.filter { when (filter) {
        ComplaintFilter.ALL -> true
        ComplaintFilter.OPEN -> it.status.isOpen
        ComplaintFilter.CLOSED -> !it.status.isOpen
    } }.sortedByDescending { it.createdAtMillis }
}

internal class ComplaintViewModel(private val repository: ComplaintRepository, private val evidence: ComplaintEvidenceStore,
    private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {
    var state by mutableStateOf(ComplaintUiState(repository.load(), repository.loadDraft() ?: newDraft()))
        private set
    private var importJob: Job? = null
    private var generation = 0

    fun prepare(orderId: String?) {
        state = state.copy(issue = null, evidenceIssue = null)
        if (orderId != null) requestOrderChange(orderId)
    }

    fun requestOrderChange(orderId: String) {
        if (state.importing || state.draft.orderId == orderId) return
        if (state.draft.hasContent) state = state.copy(pendingOrderId = orderId)
        else updateDraft(state.draft.copy(orderId = orderId))
    }

    fun dismissOrderChange() { state = state.copy(pendingOrderId = null) }
    fun confirmOrderChange() {
        val id = state.pendingOrderId ?: return
        val discarded = state.draft.evidence
        updateDraft(newDraft().copy(orderId = id))
        state = state.copy(pendingOrderId = null)
        removeFiles(discarded)
    }

    fun chooseReason(reason: ComplaintReason) { updateDraft(state.draft.copy(reason = reason)) }
    fun changeDescription(value: String) { updateDraft(state.draft.copy(description = value.take(MAX_COMPLAINT_LENGTH))) }
    fun selectFilter(filter: ComplaintFilter) { state = state.copy(filter = filter) }

    fun imagePickerUnavailable() { state = state.copy(evidenceIssue = EvidenceIssue.UNREADABLE) }

    fun addEvidence(sources: List<String>) {
        if (state.importing || sources.isEmpty()) return
        val capacity = MAX_COMPLAINT_IMAGES - state.draft.evidence.size
        if (capacity <= 0) { state = state.copy(evidenceIssue = EvidenceIssue.LIMIT_REACHED); return }
        val draftId = state.draft.id
        val owner = generation
        state = state.copy(importing = true, evidenceIssue = if (sources.size > capacity) EvidenceIssue.LIMIT_REACHED else null)
        importJob = viewModelScope.launch {
            try {
                for (source in sources.distinct().take(capacity)) {
                    when (val result = evidence.import(source)) {
                        is EvidenceImportResult.Added -> {
                            if (owner != generation || state.draft.id != draftId) {
                                withContext(NonCancellable) { evidence.remove(result.evidence) }
                            } else updateDraft(state.draft.copy(evidence = state.draft.evidence + result.evidence))
                        }
                        is EvidenceImportResult.Failed -> if (owner == generation) state = state.copy(evidenceIssue = result.issue)
                    }
                }
            } finally { if (owner == generation) state = state.copy(importing = false) }
        }
    }

    fun removeEvidence(id: String) {
        if (state.importing) return
        val removed = state.draft.evidence.firstOrNull { it.id == id } ?: return
        updateDraft(state.draft.copy(evidence = state.draft.evidence.filterNot { it.id == id }))
        state = state.copy(evidenceIssue = null)
        removeFiles(listOf(removed))
    }

    fun submit(orders: List<ComplaintOrder>): String? {
        if (state.importing) return null
        val current = repository.load()
        val issue = complaintIssue(state.draft, orders, current)
            ?: if (state.draft.evidence.any { !evidence.isAvailable(it) }) ComplaintIssue.ATTACHMENT_UNAVAILABLE else null
        if (issue != null) { state = state.copy(cases = current, issue = issue); return null }
        val draft = state.draft
        val now = clock()
        val complaint = CustomerComplaint(draft.id, orders.first { it.id == draft.orderId }, requireNotNull(draft.reason),
            draft.description.trim(), draft.evidence, ComplaintStatus.PENDING, now, now)
        if (!repository.submit(complaint)) {
            state = state.copy(issue = ComplaintIssue.NETWORK_UNAVAILABLE)
            return null
        }
        repository.saveDraft(null)
        state = ComplaintUiState(repository.load(), newDraft())
        return complaint.id
    }

    fun restore() {
        state = state.copy(cases = repository.load(), draft = if (state.importing) state.draft else repository.loadDraft() ?: newDraft())
    }

    fun clear() {
        generation++
        importJob?.cancel()
        state = ComplaintUiState(emptyList(), newDraft())
    }

    private fun updateDraft(draft: ComplaintDraft) {
        repository.saveDraft(draft)
        state = state.copy(draft = draft, issue = null)
    }

    private fun removeFiles(items: List<ComplaintEvidence>) {
        if (items.isEmpty()) return
        viewModelScope.launch { items.forEach { evidence.remove(it) } }
    }

    private companion object {
        fun newDraft() = ComplaintDraft("KN" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase(Locale.ROOT))
    }
}
