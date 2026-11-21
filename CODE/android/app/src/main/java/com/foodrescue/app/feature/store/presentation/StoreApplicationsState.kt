package com.foodrescue.app.feature.store.presentation

import com.foodrescue.app.feature.store.domain.*
import java.text.Normalizer
import java.util.Locale

internal enum class ApplicationFilter(val label: String, val status: StoreReviewStatus?) {
    PENDING("Chờ duyệt", StoreReviewStatus.PENDING),
    CHANGES("Cần bổ sung", StoreReviewStatus.CHANGES_REQUIRED),
    APPROVED("Đã duyệt", StoreReviewStatus.APPROVED),
    ALL("Tất cả", null),
}

internal data class StoreApplicationsState(
    val applications: List<StoreApplication> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val filter: ApplicationFilter = ApplicationFilter.PENDING,
    val selectedKey: String? = null,
    val decision: StoreReviewAction? = null,
    val decisionRevision: Long? = null,
    val note: String = "",
    val attempted: Boolean = false,
    val confirming: Boolean = false,
    val notice: String? = null,
) {
    val selected: StoreApplication? get() = applications.find { it.accountKey == selectedKey }
    fun count(status: StoreReviewStatus) = applications.count { it.store.status == status }
    val visible: List<StoreApplication> get() {
        val term = query.searchable()
        return applications.filter {
            (filter.status == null || it.store.status == filter.status) &&
                (term.isBlank() || listOf(it.reference, it.store.profile.name, it.store.profile.contactName,
                    it.store.profile.phone, it.store.profile.contactEmail, it.store.profile.fullAddress)
                    .any { value -> term in value.searchable() })
        }
    }
    val noteError: String? get() = if (attempted && decision?.let { !validReviewNote(it, note) } == true)
        "Nhập nội dung cụ thể từ 10 đến 1.000 ký tự." else null
}

private fun String.searchable() = Normalizer.normalize(trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
