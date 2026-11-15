package com.foodrescue.app.feature.staff.domain

internal enum class EmployeePermission { VIEW_ORDERS, PREPARE_BOXES, HAND_OVER, OPERATE_SALES }
internal enum class EmployeeStatus { ACTIVE, REVOKED }
internal enum class EmployeeFilter { ALL, ACTIVE, REVOKED }
internal enum class EmployeeEventType { CREATED, UPDATED, REVOKED, RESTORED }
internal enum class EmployeeIssue { NAME, EMAIL, PHONE, PERMISSIONS, DUPLICATE, OWNER_EMAIL, IDENTITY, STALE, NOT_FOUND, STORE_ACCESS, REASON }
internal class EmployeeException(val issue: EmployeeIssue) : IllegalArgumentException(issue.name)

internal data class EmployeeEvent(val type: EmployeeEventType, val atMillis: Long,
    val permissions: Set<EmployeePermission>, val note: String = "")
internal data class StoreEmployee(val id: String, val storeId: Int, val name: String, val email: String,
    val phone: String, val permissions: Set<EmployeePermission>, val status: EmployeeStatus,
    val revision: Int, val createdAtMillis: Long, val updatedAtMillis: Long, val events: List<EmployeeEvent>) {
    val effectivePermissions: Set<EmployeePermission> get() = if (status == EmployeeStatus.ACTIVE) permissions else emptySet()
}
internal data class EmployeeDraft(val id: String? = null, val revision: Int = 0, val name: String = "",
    val email: String = "", val phone: String = "", val permissions: Set<EmployeePermission> = emptySet())
internal fun StoreEmployee.toDraft() = EmployeeDraft(id, revision, name, email, phone, permissions)

internal interface EmployeeRepository {
    suspend fun load(): List<StoreEmployee>
    suspend fun save(draft: EmployeeDraft): StoreEmployee
    suspend fun changeAccess(id: String, revision: Int, status: EmployeeStatus, reason: String): StoreEmployee
}
internal data class StaffAccess(val employee: StoreEmployee, val storeName: String, val storeApproved: Boolean) {
    val allowed: Boolean get() = storeApproved && employee.status == EmployeeStatus.ACTIVE
    val permissions: Set<EmployeePermission> get() = if (allowed) employee.effectivePermissions else emptySet()
}
internal interface StaffAccessRepository { suspend fun find(email: String): StaffAccess? }
