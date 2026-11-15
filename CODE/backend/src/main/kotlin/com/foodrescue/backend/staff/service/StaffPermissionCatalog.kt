// AC-04,06 (T3.4): StaffPermissionCatalog responsibility.
package com.foodrescue.backend.staff.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.staff.StaffPermissionCode
import com.foodrescue.backend.staff.entity.Permission
import com.foodrescue.backend.staff.repository.PermissionRepository
import org.springframework.stereotype.Component

/** Resolves requested codes against the staff catalogue; anything else (owner, admin, price...) is rejected (BR-03). */
@Component
class StaffPermissionCatalog(private val permissions: PermissionRepository) {
    fun resolve(codes: List<String>): List<Permission> {
        val allowed = StaffPermissionCode.entries.map { it.name }.toSet()
        val errors = codes.mapIndexedNotNull { i, c ->
            when {
                c !in allowed -> FieldViolation("permissions[$i]", "Permission cannot be granted to staff")
                codes.indexOf(c) != i -> FieldViolation("permissions[$i]", "Duplicate permission")
                else -> null
            }
        }
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
        return permissions.findAllByCodeIn(codes).sortedBy { it.code }
    }
}
