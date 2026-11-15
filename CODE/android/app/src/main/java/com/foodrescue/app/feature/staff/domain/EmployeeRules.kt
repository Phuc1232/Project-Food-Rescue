package com.foodrescue.app.feature.staff.domain

import java.text.Normalizer
import java.util.Locale

internal fun employeeEmail(value: String) = value.trim().lowercase(Locale.ROOT)
private val orderDependent = setOf(EmployeePermission.PREPARE_BOXES, EmployeePermission.HAND_OVER)
internal val pickupPermissions = setOf(EmployeePermission.VIEW_ORDERS, EmployeePermission.HAND_OVER)
internal val operationsPermissions = EmployeePermission.entries.toSet()

internal fun EmployeeDraft.validationIssues(): Set<EmployeeIssue> = buildSet {
    if (name.trim().length !in 2..120 || name.any(Char::isISOControl)) add(EmployeeIssue.NAME)
    val address = employeeEmail(email)
    val local = address.substringBefore('@')
    if (address.length > 254 || local.length > 64 || local.startsWith('.') || local.endsWith('.') || ".." in address ||
        !Regex("^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$").matches(address)) add(EmployeeIssue.EMAIL)
    if (phone.isNotBlank() && !Regex("^(0[0-9]{9}|\\+84[0-9]{9})$").matches(phone.trim())) add(EmployeeIssue.PHONE)
    if (permissions.isEmpty() || (permissions.any { it in orderDependent } && EmployeePermission.VIEW_ORDERS !in permissions)) add(EmployeeIssue.PERMISSIONS)
}
internal fun Set<EmployeePermission>.toggle(permission: EmployeePermission, enabled: Boolean): Set<EmployeePermission> =
    if (enabled) this + permission + (if (permission in orderDependent) setOf(EmployeePermission.VIEW_ORDERS) else emptySet())
    else this - permission - (if (permission == EmployeePermission.VIEW_ORDERS) orderDependent else emptySet())

internal fun StoreEmployee.changeAccess(revision: Int, status: EmployeeStatus, reason: String, now: Long): StoreEmployee {
    if (revision != this.revision) throw EmployeeException(EmployeeIssue.STALE)
    if (status == this.status) return this
    if (status == EmployeeStatus.REVOKED && (reason.trim().length !in 2..300 || reason.any { it.isISOControl() && it != '\n' }))
        throw EmployeeException(EmployeeIssue.REASON)
    val event = EmployeeEvent(if (status == EmployeeStatus.REVOKED) EmployeeEventType.REVOKED else EmployeeEventType.RESTORED,
        now, permissions, if (status == EmployeeStatus.REVOKED) reason.trim() else "")
    return copy(status = status, revision = revision + 1, updatedAtMillis = now, events = events + event)
}
internal fun List<StoreEmployee>.searchEmployees(query: String, filter: EmployeeFilter): List<StoreEmployee> {
    val terms = searchable(query).split(Regex("\\s+")).filter(String::isNotBlank)
    return filter { member ->
        (filter == EmployeeFilter.ALL || member.status.name == filter.name) &&
            terms.all { it in searchable("${member.name} ${member.email} ${member.phone}") }
    }.sortedWith(compareBy<StoreEmployee> { it.status != EmployeeStatus.ACTIVE }.thenBy { searchable(it.name) }.thenBy { it.id })
}
private fun searchable(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace('đ', 'd').trim()
