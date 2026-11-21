package com.foodrescue.app.feature.account.data

import android.content.Context
import com.foodrescue.app.feature.account.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LocalAccountSessionRepository(context: Context,
    private val clock: () -> Long = System::currentTimeMillis) : AccountSessionRepository {
    private val records = LocalAccountStore(context)
    override suspend fun register(name: String, email: String): Unit = withContext(Dispatchers.IO) {
        val address = accountEmail(email)
        require(address.isNotBlank() && '@' in address && address.length <= 254 && address.none(Char::isWhitespace))
        check(!com.foodrescue.app.feature.auth.domain.AdminIdentity.matches(address))
        records.change(LocalAccountStore.key(address)) { before ->
            if (before.status == AccountStatus.SUSPENDED) throw AccountSuspended(before.events.last().reason)
            if (before.profile != null) before else before.copy(
                profile = CustomerProfile(name.trim().ifBlank { address.substringBefore('@') }.take(120), address),
                firstSeenAt = before.firstSeenAt ?: clock(), revision = before.revision + 1)
        }
        Unit
    }
    override suspend fun signIn(name: String, email: String): AccountLogin = withContext(Dispatchers.IO) {
        val address = accountEmail(email)
        require(address.isNotBlank() && address.length <= 254 && '@' in address && address.none(Char::isWhitespace))
        val record = records.change(LocalAccountStore.key(address)) { before ->
            if (before.status == AccountStatus.SUSPENDED) throw AccountSuspended(before.events.last().reason)
            val profile = before.profile ?: CustomerProfile(name.trim().ifBlank { address.substringBefore('@') }.take(120), address)
            val now = clock()
            before.copy(profile = profile, firstSeenAt = before.firstSeenAt ?: now,
                lastLoginAt = now, revision = before.revision + 1)
        }
        AccountLogin(checkNotNull(record.profile), record.accessVersion)
    }
    override suspend fun access(email: String): AccountRecord = withContext(Dispatchers.IO) {
        records.read(LocalAccountStore.key(email))
    }
    override suspend fun updateProfile(profile: CustomerProfile, accessVersion: Long): CustomerProfile = withContext(Dispatchers.IO) {
        require(isValidCustomerName(profile.name) && isValidCustomerPhone(profile.phone))
        val value = profile.copy(name = profile.name.trim(), email = accountEmail(profile.email), phone = profile.phone.trim())
        records.change(LocalAccountStore.key(value.email)) {
            if (it.status == AccountStatus.SUSPENDED) throw AccountSuspended(it.events.last().reason)
            check(it.profile != null)
            check(it.accessVersion == accessVersion)
            it.copy(profile = value, revision = it.revision + 1)
        }
        value
    }
}

