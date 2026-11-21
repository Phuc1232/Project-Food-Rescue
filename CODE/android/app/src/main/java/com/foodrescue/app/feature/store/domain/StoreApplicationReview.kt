package com.foodrescue.app.feature.store.domain

internal enum class StoreReviewAction { SUBMITTED, APPROVED, CHANGES_REQUESTED }

internal data class StoreReviewEvent(
    val action: StoreReviewAction,
    val atMillis: Long,
    val actor: String,
    val note: String = "",
    val submittedProfile: OwnerStoreProfile? = null,
)

internal data class StoreApplication(val accountKey: String, val storeId: Int, val store: OwnerStore) {
    val reference: String get() = "HS-${-storeId.toLong()}"
}

internal interface StoreApplicationRepository {
    suspend fun applications(): List<StoreApplication>
    suspend fun review(accountKey: String, expectedRevision: Long, action: StoreReviewAction, note: String): StoreApplication
}

internal class StaleStoreApplication : IllegalStateException("Application changed")

internal fun validReviewNote(action: StoreReviewAction, note: String): Boolean =
    note.trim().length in (if (action == StoreReviewAction.CHANGES_REQUESTED) 10 else 0)..1000 &&
        note.none { it.isISOControl() && it != '\n' }

internal fun OwnerStore.review(expectedRevision: Long, action: StoreReviewAction, note: String,
    actor: String, nowMillis: Long): OwnerStore {
    if (revision != expectedRevision || status != StoreReviewStatus.PENDING) throw StaleStoreApplication()
    require(action != StoreReviewAction.SUBMITTED)
    require(actor.isNotBlank() && validReviewNote(action, note))
    if (action == StoreReviewAction.APPROVED) require(profile.validationErrors().isEmpty())
    return copy(
        status = if (action == StoreReviewAction.APPROVED) StoreReviewStatus.APPROVED else StoreReviewStatus.CHANGES_REQUIRED,
        reviewNote = note.trim(), revision = revision + 1,
        reviewHistory = reviewHistory + StoreReviewEvent(action, nowMillis, actor, note.trim()),
    )
}
