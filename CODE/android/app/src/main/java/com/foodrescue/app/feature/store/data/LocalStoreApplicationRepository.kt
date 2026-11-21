package com.foodrescue.app.feature.store.data

import android.content.Context
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal class LocalStoreApplicationRepository(context: Context, email: String) : StoreApplicationRepository {
    private val actor = email.trim().lowercase(Locale.ROOT)
    private val records = LocalStoreRecords(context)
    private val directory = LocalPublicStoreRepository(context)

    override suspend fun applications(): List<StoreApplication> = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        directory.registeredAccounts().mapNotNull { (key, id) ->
            records.read(key)?.takeIf { it.status != StoreReviewStatus.DRAFT }?.let { StoreApplication(key, id, it) }
        }.sortedBy { it.store.submittedAtMillis ?: Long.MAX_VALUE }
    }

    override suspend fun review(accountKey: String, expectedRevision: Long, action: StoreReviewAction,
        note: String): StoreApplication = withContext(Dispatchers.IO) {
        check(AdminIdentity.matches(actor))
        val id = directory.registeredAccounts()[accountKey] ?: throw StaleStoreApplication()
        val store = records.change(accountKey, { throw StaleStoreApplication() }) {
            it.review(expectedRevision, action, note, actor, System.currentTimeMillis())
        }
        StoreApplication(accountKey, id, store)
    }
}
