package com.foodrescue.app.feature.account.domain

internal enum class AccountRole { CUSTOMER, OWNER, STAFF, ADMIN }
internal enum class AccountStatus { ACTIVE, SUSPENDED }
internal data class AccountStatusEvent(val id: String, val status: AccountStatus, val reason: String,
    val actor: String, val atMillis: Long)
internal data class AccountRecord(val key: String, val profile: CustomerProfile? = null,
    val firstSeenAt: Long? = null, val lastLoginAt: Long? = null, val revision: Long = 0,
    val events: List<AccountStatusEvent> = emptyList()) {
    init {
        require(key.matches(Regex("[0-9a-f]{64}")) && revision >= events.size)
        require(events.map { it.id }.distinct().size == events.size)
        events.forEachIndexed { index, event ->
            require(event.id.isNotBlank() && event.actor.isNotBlank() && validAccountReason(event.reason))
            require(event.atMillis >= (events.getOrNull(index - 1)?.atMillis ?: 0))
            require(event.status != (events.getOrNull(index - 1)?.status ?: AccountStatus.ACTIVE))
        }
    }
    val status: AccountStatus get() = events.lastOrNull()?.status ?: AccountStatus.ACTIVE
    val accessVersion: Long get() = events.size.toLong()
}
internal data class AccountRelationship(val role: AccountRole, val storeId: Int, val storeName: String,
    val status: String, val permissions: List<String> = emptyList())
internal data class ManagedAccount(val record: AccountRecord, val name: String, val email: String?,
    val phone: String, val roles: Set<AccountRole>, val relationships: List<AccountRelationship> = emptyList(),
    val orderCount: Int = 0, val pendingOrders: Int = 0) {
    val key: String get() = record.key
    val reference: String get() = "TK-" + key.take(10).uppercase()
    val protected: Boolean get() = AccountRole.ADMIN in roles
}
internal data class AccountDecision(val id: String, val expectedRevision: Long,
    val status: AccountStatus, val reason: String = "")
internal class StaleAccountDecision : IllegalStateException()
internal class ProtectedAccount : IllegalStateException()
internal class AccountSuspended(val reason: String) : IllegalStateException()
internal fun validAccountReason(reason: String) = reason.trim().length in 10..500 &&
    reason.none { it.isISOControl() && it != '\n' }

internal fun AccountRecord.changeStatus(draft: AccountDecision, actor: String, now: Long,
    protected: Boolean, isSelf: Boolean): AccountRecord {
    if (protected || isSelf) throw ProtectedAccount()
    require(draft.id.isNotBlank() && actor.isNotBlank() && validAccountReason(draft.reason))
    if (revision != draft.expectedRevision || status == draft.status || events.any { it.id == draft.id })
        throw StaleAccountDecision()
    require(now >= (events.lastOrNull()?.atMillis ?: 0))
    return copy(revision = revision + 1, events = events + AccountStatusEvent(
        draft.id, draft.status, draft.reason.trim(), actor, now))
}
internal data class AccountLogin(val profile: CustomerProfile, val accessVersion: Long)
internal interface AccountSessionRepository {
    suspend fun register(name: String, email: String)
    suspend fun signIn(name: String, email: String): AccountLogin
    suspend fun access(email: String): AccountRecord
    suspend fun updateProfile(profile: CustomerProfile, accessVersion: Long): CustomerProfile
}
internal interface AccountManagementRepository {
    suspend fun accounts(): List<ManagedAccount>
    suspend fun decide(key: String, decision: AccountDecision): ManagedAccount
}

