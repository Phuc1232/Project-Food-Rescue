package com.foodrescue.app.feature.complaint.domain

internal interface ComplaintRepository {
    fun load(): List<CustomerComplaint>
    fun loadDraft(): ComplaintDraft?
    fun saveDraft(draft: ComplaintDraft?)
    fun submit(complaint: CustomerComplaint): Boolean
}

internal enum class EvidenceIssue { UNSUPPORTED, TOO_LARGE, UNREADABLE, LIMIT_REACHED }
internal sealed interface EvidenceImportResult {
    data class Added(val evidence: ComplaintEvidence) : EvidenceImportResult
    data class Failed(val issue: EvidenceIssue) : EvidenceImportResult
}

internal interface ComplaintEvidenceStore {
    suspend fun import(sourceUri: String): EvidenceImportResult
    suspend fun remove(evidence: ComplaintEvidence)
    fun isAvailable(evidence: ComplaintEvidence): Boolean
}
