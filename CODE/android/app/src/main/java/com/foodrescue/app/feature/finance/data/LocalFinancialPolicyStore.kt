package com.foodrescue.app.feature.finance.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.auth.domain.AdminIdentity
import com.foodrescue.app.feature.finance.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalFinancialPolicyStore(context: Context) : FinancialTermsSource {
    private val prefs = context.applicationContext.getSharedPreferences("financial_policy.v1", Context.MODE_PRIVATE)
    fun read(): FinancialPolicyBook = synchronized(LocalCommerceLock) {
        prefs.getString("book", null)?.let(FinancialPolicyJson::decode) ?: FinancialPolicyBook()
    }
    override fun at(time: Long): FinancialTerms = read().termsAt(time)
    @SuppressLint("UseKtx", "ApplySharedPref")
    fun change(transform: (FinancialPolicyBook) -> FinancialPolicyBook): FinancialPolicyBook = synchronized(LocalCommerceLock) {
        val before = prefs.getString("book", null)
        val next = transform(read())
        if (!prefs.edit().putString("book", FinancialPolicyJson.encode(next)).commit()) {
            prefs.edit().putString("book", before).commit()
            error("Policy write failed")
        }
        next
    }
}

internal class LocalFinancialPolicyRepository(context: Context, private val actor: String,
    private val clock: () -> Long = System::currentTimeMillis) : FinancialPolicyRepository {
    private val store = LocalFinancialPolicyStore(context)
    override suspend fun load() = withContext(Dispatchers.IO) { check(AdminIdentity.matches(actor)); store.read() }
    override suspend fun publish(expectedRevision: Long, terms: FinancialTerms, effectiveAt: Long, reason: String) =
        withContext(Dispatchers.IO) {
            check(AdminIdentity.matches(actor))
            store.change { val now = clock(); it.publish(expectedRevision, terms, if (effectiveAt == 0L) now else effectiveAt, actor, reason, now) }
        }
}
