package com.foodrescue.app.feature.staff.data

import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.store.domain.OwnerStoreRepository
import com.foodrescue.app.feature.store.domain.StoreReviewStatus
import java.util.UUID

internal class LocalEmployeeRepository(email: String, private val storeId: Int, private val owner: OwnerStoreRepository,
    private val directory: LocalEmployeeDirectory, private val clock: () -> Long = System::currentTimeMillis) : EmployeeRepository {
    private val ownerEmail = employeeEmail(email)
    override suspend fun load(): List<StoreEmployee> {
        requireApproved()
        return directory.read().filter { it.ownerEmail == ownerEmail && it.employee.storeId == storeId }.map { it.employee }
    }
    override suspend fun save(draft: EmployeeDraft): StoreEmployee {
        draft.validationIssues().firstOrNull()?.let { throw EmployeeException(it) }
        var result: StoreEmployee? = null
        directory.update { records ->
            requireApproved()
            val address = employeeEmail(draft.email)
            val before = draft.id?.let { id -> records.find { it.ownerEmail == ownerEmail && it.employee.storeId == storeId && it.employee.id == id }?.employee
                ?: throw EmployeeException(EmployeeIssue.NOT_FOUND) }
            if (before != null) {
                if (before.revision != draft.revision) throw EmployeeException(EmployeeIssue.STALE)
                if (before.email != address) throw EmployeeException(EmployeeIssue.IDENTITY)
                if (before.status != EmployeeStatus.ACTIVE) throw EmployeeException(EmployeeIssue.STALE)
            } else {
                if (address == ownerEmail || directory.isOwner(address)) throw EmployeeException(EmployeeIssue.OWNER_EMAIL)
                if (records.any { it.employee.email == address }) throw EmployeeException(EmployeeIssue.DUPLICATE)
            }
            val now = clock()
            val changed = before == null || before.name != draft.name.trim() || before.phone != draft.phone.trim() || before.permissions != draft.permissions
            val member = if (!changed) checkNotNull(before) else StoreEmployee(before?.id ?: UUID.randomUUID().toString(), storeId,
                draft.name.trim(), address, draft.phone.trim(), draft.permissions.toSet(), EmployeeStatus.ACTIVE,
                (before?.revision ?: 0) + 1, before?.createdAtMillis ?: now, now,
                before?.events.orEmpty() + EmployeeEvent(if (before == null) EmployeeEventType.CREATED else EmployeeEventType.UPDATED, now, draft.permissions.toSet()))
            result = member
            if (before == null) records + EmployeeRecord(ownerEmail, member)
            else records.map { if (it.employee.id == member.id) it.copy(employee = member) else it }
        }
        return checkNotNull(result)
    }
    override suspend fun changeAccess(id: String, revision: Int, status: EmployeeStatus, reason: String): StoreEmployee {
        var result: StoreEmployee? = null
        directory.update { records ->
            requireApproved()
            val record = records.find { it.ownerEmail == ownerEmail && it.employee.storeId == storeId && it.employee.id == id }
                ?: throw EmployeeException(EmployeeIssue.NOT_FOUND)
            val changed = record.employee.changeAccess(revision, status, reason, clock())
            result = changed
            records.map { if (it.employee.id == id) it.copy(employee = changed) else it }
        }
        return checkNotNull(result)
    }
    private suspend fun requireApproved() {
        if (owner.load().status != StoreReviewStatus.APPROVED) throw EmployeeException(EmployeeIssue.STORE_ACCESS)
    }
}
