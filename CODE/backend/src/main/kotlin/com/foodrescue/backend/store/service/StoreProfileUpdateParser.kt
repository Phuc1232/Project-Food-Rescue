// AC-03,11: StoreProfileUpdateParser responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.store.dto.OpeningHourRequest
import com.foodrescue.backend.store.dto.StoreProfileUpdateCommand
import org.springframework.stereotype.Component

/**
 * Raw JSON -> command so every non-editable field is reported by name (T3.3 P2, same approach as T2.1). D04-bound fields
 * (name, address, location) go through change requests; status is admin-only (UC-41 E2).
 */
@Component
class StoreProfileUpdateParser {
    fun parse(body: Map<String, Any?>?): StoreProfileUpdateCommand {
        if (body == null) throw ApiException(400, "INVALID_REQUEST", "Invalid request")
        val errors = sortedMapOf<String, String>()
        body.keys.filter { it !in EDITABLE }.forEach { errors[it] = if (it in REVIEWED) REVIEW_REQUIRED else NOT_EDITABLE }
        val version = (body[VERSION] as? Number)?.takeIf { it is Int || it is Long }?.toLong()?.takeIf { it >= 0 }
        if (version == null) errors[VERSION] = INVALID
        val descriptionPresent = DESCRIPTION in body
        val description = when (val d = body[DESCRIPTION]) {
            null -> null
            is String -> d.trim().ifEmpty { null }?.also { if (it.length > MAX_DESCRIPTION) errors[DESCRIPTION] = "Must be at most $MAX_DESCRIPTION characters" }
            else -> { errors[DESCRIPTION] = INVALID; null }
        }
        val hours = if (OPENING_HOURS !in body) null else when (val h = body[OPENING_HOURS]) {
            is List<*> -> when {
                h.isEmpty() -> { errors[OPENING_HOURS] = "At least one shift is required"; null }
                h.size > MAX_SHIFTS -> { errors[OPENING_HOURS] = "At most $MAX_SHIFTS shifts"; null }
                else -> h.mapIndexed { i, item -> shift(item) ?: run { errors["$OPENING_HOURS[$i]"] = INVALID; null } }
            }
            else -> { errors[OPENING_HOURS] = INVALID; null }
        }
        if (errors.isNotEmpty())
            throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors.map { FieldViolation(it.key, it.value) })
        return StoreProfileUpdateCommand(version!!, descriptionPresent, description, hours?.map { it!! })
    }

    /** Shape only; OpeningHoursValidator checks days, times, overlaps. */
    private fun shift(item: Any?): OpeningHourRequest? {
        val m = item as? Map<*, *> ?: return null
        if (m.keys.any { it !in SHIFT_KEYS }) return null
        val day = m["dayOfWeek"] as? Int ?: return null
        return OpeningHourRequest(day, m["opensAt"] as? String ?: return null, m["closesAt"] as? String ?: return null)
    }

    private companion object {
        const val VERSION = "version"
        const val DESCRIPTION = "description"
        const val OPENING_HOURS = "openingHours"
        const val MAX_DESCRIPTION = 2000
        const val MAX_SHIFTS = 21
        const val INVALID = "Invalid value"
        const val NOT_EDITABLE = "Field is not editable"
        const val REVIEW_REQUIRED = "Requires review: use POST /api/v1/stores/{storeId}/change-requests"
        val EDITABLE = setOf(VERSION, DESCRIPTION, OPENING_HOURS)
        val REVIEWED = setOf("name", "address", "latitude", "longitude")
        val SHIFT_KEYS = setOf("dayOfWeek", "opensAt", "closesAt")
    }
}
