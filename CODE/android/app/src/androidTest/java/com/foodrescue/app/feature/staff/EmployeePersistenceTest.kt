package com.foodrescue.app.feature.staff

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.foodrescue.app.feature.staff.data.*
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.store.domain.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EmployeePersistenceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "employees_qa_${UUID.randomUUID()}_"
    private val files = mutableSetOf<String>()
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = synchronized(files) {
            files += prefix + name; base.getSharedPreferences(prefix + name, mode)
        }
    }
    private val directory = LocalEmployeeDirectory(context)
    private val owner = LocalOwnerStoreRepository(context, "owner@foodrescue.vn")
    private fun repo(email: String = "owner@foodrescue.vn", source: OwnerStoreRepository = owner) =
        LocalEmployeeRepository(email, if (email == "owner@foodrescue.vn") -1 else -2, source, directory)
    private val draft = EmployeeDraft(name = "Nguyễn An", email = "an@example.com", phone = "0901234567", permissions = pickupPermissions)
    @After fun cleanup() { files.toList().forEach { base.deleteSharedPreferences(it) } }
    private suspend fun expectIssue(issue: EmployeeIssue, block: suspend () -> Unit) {
        try { block(); fail("Expected $issue") } catch (error: EmployeeException) { assertEquals(issue, error.issue) }
    }
    @Test fun createAndRevokeSurviveRestartAndImmediatelyChangeAccess() = runBlocking {
        val member = repo().save(draft)
        assertEquals("an@example.com", member.email)
        assertEquals(member, repo().load().single())
        assertTrue(directory.find(" AN@EXAMPLE.COM ")!!.allowed)
        val revoked = repo().changeAccess(member.id, member.revision, EmployeeStatus.REVOKED, "Nghỉ việc")
        val restoredDirectory = LocalEmployeeDirectory(context)
        assertFalse(restoredDirectory.find(member.email)!!.allowed)
        assertTrue(restoredDirectory.find(member.email)!!.permissions.isEmpty())
        assertEquals(2, repo().load().single().events.size)
        repo().changeAccess(member.id, revoked.revision, EmployeeStatus.ACTIVE, "")
        assertEquals(pickupPermissions, restoredDirectory.find(member.email)!!.permissions)
    }
    @Test fun ownerScopesPreventReadingAndMutatingAnotherStore() = runBlocking {
        val member = repo().save(draft)
        val other = repo("other-owner@example.com")
        assertTrue(other.load().isEmpty())
        expectIssue(EmployeeIssue.NOT_FOUND) { other.save(member.toDraft().copy(name = "Khác")) }
        expectIssue(EmployeeIssue.NOT_FOUND) { other.changeAccess(member.id, 1, EmployeeStatus.REVOKED, "Nghỉ việc") }
        assertEquals(member, repo().load().single())
    }
    @Test fun duplicateEmailIsAtomicAcrossRepositoryInstancesAndIncludesRevoked() = runBlocking {
        val outcomes = (1..2).map { async(Dispatchers.Default) {
            runCatching { repo().save(draft.copy(email = " AN@example.com ")) }.isSuccess
        } }.awaitAll()
        assertEquals(1, outcomes.count { it })
        val member = repo().load().single()
        repo().changeAccess(member.id, 1, EmployeeStatus.REVOKED, "Nghỉ việc")
        expectIssue(EmployeeIssue.DUPLICATE) { repo().save(draft) }
        expectIssue(EmployeeIssue.DUPLICATE) { repo("other@example.com").save(draft) }
    }
    @Test fun ownerEmailAndUnapprovedStoreAreRejected() = runBlocking {
        expectIssue(EmployeeIssue.OWNER_EMAIL) { repo().save(draft.copy(email = "Owner@FoodRescue.vn")) }
        val pending = object : OwnerStoreRepository by owner { override suspend fun load() = owner.load().copy(status = StoreReviewStatus.PENDING) }
        expectIssue(EmployeeIssue.STORE_ACCESS) { repo(source = pending).save(draft) }
    }
    @Test fun staleEditorCannotUndoRevocationOrOverwriteNewPermissions() = runBlocking {
        val member = repo().save(draft)
        repo().changeAccess(member.id, 1, EmployeeStatus.REVOKED, "Nghỉ việc")
        expectIssue(EmployeeIssue.STALE) { repo().save(member.toDraft().copy(permissions = operationsPermissions)) }
        assertEquals(EmployeeStatus.REVOKED, repo().load().single().status)
        assertTrue(directory.find(member.email)!!.permissions.isEmpty())
    }
    @Test fun emailIsImmutableAndUnchangedSaveDoesNotAddHistory() = runBlocking {
        val member = repo().save(draft)
        expectIssue(EmployeeIssue.IDENTITY) { repo().save(member.toDraft().copy(email = "new@example.com")) }
        assertEquals(member, repo().save(member.toDraft()))
        val changed = repo().save(member.toDraft().copy(permissions = operationsPermissions))
        assertEquals(2, changed.revision)
        assertEquals(2, changed.events.size)
        assertEquals(operationsPermissions, directory.find(member.email)!!.permissions)
    }
    @Test fun corruptDirectoryCannotBeOverwrittenOrGrantAccess() = runBlocking {
        repo().save(draft)
        val prefs = context.getSharedPreferences("employee_directory.v1", Context.MODE_PRIVATE)
        prefs.edit().putString("directory", "{broken").commit()
        assertTrue(runCatching { repo().load() }.isFailure)
        assertTrue(runCatching { directory.find(draft.email) }.isFailure)
        assertTrue(runCatching { repo().save(draft.copy(email = "b@example.com")) }.isFailure)
        assertEquals("{broken", prefs.getString("directory", null))
    }
}
