package com.foodrescue.app.feature.store.domain

internal interface OwnerStoreRepository {
    suspend fun load(): OwnerStore
    suspend fun saveDraft(profile: OwnerStoreProfile): OwnerStore
    suspend fun submit(profile: OwnerStoreProfile): OwnerStore
    suspend fun updateProfile(profile: OwnerStoreProfile): OwnerStore
    suspend fun updatePayout(payout: StorePayoutAccount): OwnerStore
}
