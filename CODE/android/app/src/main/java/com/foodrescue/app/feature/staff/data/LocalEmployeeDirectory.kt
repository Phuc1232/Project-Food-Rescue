package com.foodrescue.app.feature.staff.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.feature.staff.domain.*
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.store.domain.StoreReviewStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One commit owns membership and its audit trail; no second index can drift from it. */
internal class LocalEmployeeDirectory(context: Context) : StaffAccessRepository {
    private val context = context.applicationContext
    private val preferences = this.context.getSharedPreferences("employee_directory.v1", Context.MODE_PRIVATE)

    internal suspend fun read(): List<EmployeeRecord> = withContext(Dispatchers.IO) { lock.withLock { readLocked() } }
    @SuppressLint("UseKtx")
    internal suspend fun update(transform: suspend (List<EmployeeRecord>) -> List<EmployeeRecord>): List<EmployeeRecord> = withContext(Dispatchers.IO) {
        lock.withLock {
            val before = readLocked()
            val after = transform(before)
            if (after != before) check(preferences.edit().putString("directory", encodeEmployees(after)).commit()) { "Employee write failed" }
            after
        }
    }
    internal fun isOwner(email: String): Boolean = LocalOwnerStoreRepository(context, email).hasApplication()
    override suspend fun find(email: String): StaffAccess? = withContext(Dispatchers.IO) {
        lock.withLock {
            val record = readLocked().firstOrNull { it.employee.email == employeeEmail(email) } ?: return@withLock null
            val store = LocalOwnerStoreRepository(context, record.ownerEmail).load()
            StaffAccess(record.employee, store.profile.name, store.status == StoreReviewStatus.APPROVED)
        }
    }
    private fun readLocked() = preferences.getString("directory", null)?.let(::decodeEmployees) ?: emptyList()
    private companion object { val lock = Mutex() }
}
