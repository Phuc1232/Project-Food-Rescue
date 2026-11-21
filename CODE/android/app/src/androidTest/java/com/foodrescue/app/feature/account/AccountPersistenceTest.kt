package com.foodrescue.app.feature.account

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.account.data.*
import com.foodrescue.app.feature.account.domain.*
import com.foodrescue.app.feature.account.presentation.*
import com.foodrescue.app.feature.staff.data.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.store.data.*
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Namespaced preferences keep account mutations away from the user's actual accounts. */
class AccountPersistenceTest {
    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "account_test_${UUID.randomUUID()}_"
        private val names = mutableSetOf<String>()
        var failNextWrite = false
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            synchronized(names) { names += prefix + name }
            val actual = baseContext.getSharedPreferences(prefix + name, mode)
            if (name != "account_directory.v1") return actual
            return object : SharedPreferences by actual {
                override fun edit(): SharedPreferences.Editor {
                    val editor = actual.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                        override fun commit(): Boolean {
                            val result = editor.commit()
                            if (failNextWrite) { failNextWrite = false; return false }
                            return result
                        }
                    }
                }
            }
        }
        fun cleanup() { names.forEach { baseContext.deleteSharedPreferences(it) } }
    }
    private val context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
    private val session = LocalAccountSessionRepository(context) { 1000 }
    private val admin get() = LocalAccountManagementRepository(context, "admin@foodrescue.vn") { 2000 }
    private val email = "an@example.com"
    private val key = LocalAccountStore.key(email)
    private val note = "Đã đối chiếu thông tin và xác nhận trạng thái."
    private suspend fun decide(status: AccountStatus, address: String = email): ManagedAccount {
        val user = admin.accounts().first { it.key == LocalAccountStore.key(address) }
        return admin.decide(user.key, AccountDecision(UUID.randomUUID().toString(), user.record.revision, status, note))
    }
    private suspend inline fun <reified T: Throwable> fails(noinline action: suspend () -> Unit) {
        val error = runCatching { action() }.exceptionOrNull()
        assertTrue("Expected ${T::class.java.simpleName}, got $error", error is T)
    }
    @After fun cleanup() { context.cleanup() }

    @Test fun normalizedRegistrationAndLoginHaveOneIdentityAndRetainEditedProfile() = runBlocking {
        session.register("Nguyễn An", " AN@EXAMPLE.COM ")
        assertNull(session.access(email).lastLoginAt)
        val login = session.signIn("an", email)
        session.updateProfile(login.profile.copy(name = "An Nguyễn", phone = "0901234567"), login.accessVersion)
        val again = LocalAccountSessionRepository(context).signIn("an", "AN@example.com")
        assertEquals("An Nguyễn", again.profile.name); assertEquals("0901234567", again.profile.phone)
        assertEquals(1, admin.accounts().count { it.key == key })
    }
    @Test fun fallbackDisplayNameCannotMakeTheDirectoryUnreadable() = runBlocking {
        val address = "a".repeat(130) + "@example.com"
        session.register("", address)
        val login = session.signIn("", address)
        assertEquals(120, login.profile.name.length)
        assertEquals(login.profile, session.access(address).profile)
        assertTrue(admin.accounts().any { it.email == address })
    }
    @Test fun suspensionBlocksLoginRegistrationProfileWriteAndReactivationExpiresOldSession() = runBlocking {
        val login = session.signIn("Nguyễn An", email)
        decide(AccountStatus.SUSPENDED)
        fails<AccountSuspended> { session.signIn("An", email) }
        fails<AccountSuspended> { session.register("An", email) }
        fails<AccountSuspended> { session.updateProfile(login.profile, 0) }
        val active = decide(AccountStatus.ACTIVE)
        assertEquals(2L, active.record.accessVersion)
        fails<IllegalStateException> { session.updateProfile(login.profile, 0) }
        assertEquals(2L, session.signIn("An", email).accessVersion)
    }
    @Test fun accountStatusAndAuditRollbackTogetherWhenCommitFails() = runBlocking {
        session.signIn("Nguyễn An", email); val before = session.access(email)
        context.failNextWrite = true
        fails<IllegalStateException> { decide(AccountStatus.SUSPENDED) }
        assertEquals(before, LocalAccountSessionRepository(context).access(email))
        decide(AccountStatus.SUSPENDED); assertEquals(1, session.access(email).events.size)
    }
    @Test fun duplicateConcurrentDecisionsHaveOnlyOneWinner() = runBlocking {
        session.signIn("An", email)
        val before = session.access(email)
        val draft = AccountDecision("decision", before.revision, AccountStatus.SUSPENDED, note)
        val outcomes = List(2) { async { runCatching { admin.decide(key, draft) } } }.awaitAll()
        assertEquals(1, outcomes.count { it.isSuccess }); assertEquals(1, session.access(email).events.size)
        assertTrue(outcomes.single { it.isFailure }.exceptionOrNull() is StaleAccountDecision)
    }
    @Test fun staleProfileRevisionRequiresNewDecision() = runBlocking {
        val login = session.signIn("An", email); val before = session.access(email)
        session.updateProfile(login.profile.copy(name = "An Nguyễn"), 0)
        fails<StaleAccountDecision> { admin.decide(key, AccountDecision("old", before.revision, AccountStatus.SUSPENDED, note)) }
        assertEquals(AccountStatus.ACTIVE, session.access(email).status)
    }
    @Test fun adminIsProtectedAndOtherAccountsCannotAdministerUsers() = runBlocking {
        val protected = admin.accounts().single()
        assertTrue(protected.protected)
        fails<ProtectedAccount> { admin.decide(protected.key, AccountDecision("self", 0, AccountStatus.SUSPENDED, note)) }
        fails<IllegalStateException> { LocalAccountManagementRepository(context, email).accounts() }
    }
    @Test fun malformedDirectoryFailsClosedWithoutReplacingItsContents() = runBlocking {
        val prefs = context.getSharedPreferences("account_directory.v1", Context.MODE_PRIVATE)
        prefs.edit().putString("records", "broken-json").commit()
        fails<Exception> { session.signIn("An", email) }
        fails<Exception> { admin.accounts() }
        val gate = AccountAccessViewModel(session); gate.refresh(email, 0)
        assertTrue(gate.state.error); assertFalse(gate.state.allowed)
        assertEquals("broken-json", prefs.getString("records", null))
    }
    @Test fun legacyOwnerDoesNotAcquireContactEmailAsLoginIdentity() = runBlocking {
        val store = OwnerStore(OwnerStoreProfile(name = "Bánh Mộc", contactName = "An Nguyễn", contactEmail = "contact@example.com"), StoreReviewStatus.APPROVED)
        val savedStore = LocalStoreRecords(context).change(key, { OwnerStore() }) { store }
        val account = admin.accounts().first { it.key == key }
        assertNull(account.email); assertTrue(AccountRole.OWNER in account.roles)
        decide(AccountStatus.SUSPENDED)
        assertEquals(savedStore, LocalStoreRecords(context).read(key))
        assertFalse(admin.accounts().any { it.email == "contact@example.com" })
        fails<AccountSuspended> { session.signIn("An", email) }
    }
    @Test fun reactivationDoesNotRestoreRevokedEmployeePermissionsOrAlterStore() = runBlocking {
        val ownerEmail = "owner@example.com"
        val ownerKey = LocalAccountStore.key(ownerEmail)
        val store = OwnerStore(OwnerStoreProfile(name = "Bánh Mộc"), StoreReviewStatus.APPROVED)
        val savedStore = LocalStoreRecords(context).change(ownerKey, { OwnerStore() }) { store }
        val storeId = LocalPublicStoreRepository(context).registeredAccounts().getValue(ownerKey)
        val permissions = pickupPermissions
        val member = StoreEmployee("staff", storeId, "Nguyễn An", email, "0901234567", permissions,
            EmployeeStatus.REVOKED, 2, 100, 200,
            listOf(EmployeeEvent(EmployeeEventType.CREATED, 100, permissions), EmployeeEvent(EmployeeEventType.REVOKED, 200, permissions, note)))
        val employees = LocalEmployeeDirectory(context)
        employees.update { listOf(EmployeeRecord(ownerEmail, member)) }
        session.signIn("An", email)
        decide(AccountStatus.SUSPENDED); decide(AccountStatus.ACTIVE)
        val account = admin.accounts().first { it.key == key }
        assertTrue(AccountRole.STAFF in account.roles)
        assertTrue(account.relationships.single().permissions.isEmpty())
        assertEquals(member, employees.find(email)!!.employee); assertFalse(employees.find(email)!!.allowed)
        assertEquals(savedStore, LocalStoreRecords(context).read(ownerKey))
    }
    @Test fun accessGateRejectsMissingRecordsAndOldVersions() = runBlocking {
        val vm = AccountAccessViewModel(session)
        vm.refresh(email, 0); assertTrue(vm.state.expired)
        session.signIn("An", email); vm.refresh(email, 0); assertTrue(vm.state.allowed)
        decide(AccountStatus.SUSPENDED); vm.refresh(email, 0); assertEquals(note, vm.state.reason)
        decide(AccountStatus.ACTIVE); vm.refresh(email, 0); assertTrue(vm.state.expired)
        vm.refresh(email, 2); assertTrue(vm.state.allowed)
    }
    @Test fun mismatchedIdentityAndDuplicatedRecordsAreRejectedByCodec() {
        val record = AccountRecord(key, CustomerProfile("An", email))
        val raw = AccountJson.encode(listOf(record)).replace(email, "someone@example.com")
        assertThrows(IllegalArgumentException::class.java) { AccountJson.decode(raw) }
        assertThrows(IllegalArgumentException::class.java) { AccountJson.decode(AccountJson.encode(listOf(record, record))) }
    }
}
