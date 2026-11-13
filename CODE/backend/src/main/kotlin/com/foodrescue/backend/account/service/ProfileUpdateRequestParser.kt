// AC-02,03: ProfileUpdateRequestParser responsibility.
package com.foodrescue.backend.account.service

import com.foodrescue.backend.account.dto.ProfileUpdateCommand
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.FieldViolation
import org.springframework.stereotype.Component

/**
 * Turns a raw JSON object into a [ProfileUpdateCommand], reporting every rejected field by name.
 * A typed DTO cannot do this: Jackson's fail-on-unknown-properties rejects unknown or mistyped
 * fields before validation, without naming them (T2.1 plan, decision P2).
 */
@Component
class ProfileUpdateRequestParser {
    fun parse(body: Map<String, Any?>?): ProfileUpdateCommand {
        if (body == null) throw ApiException(400, "INVALID_REQUEST", "Invalid request")
        val violations = sortedMapOf<String, String>()
        body.keys.filter { it !in EDITABLE }.forEach { violations[it] = NOT_EDITABLE }
        val displayName = displayName(body[DISPLAY_NAME]) ?: run { violations[DISPLAY_NAME] = INVALID; null }
        val version = version(body[VERSION]) ?: run { violations[VERSION] = INVALID; null }
        if (violations.isNotEmpty())
            throw ApiException(400, "VALIDATION_FAILED", "Invalid request", violations.map { FieldViolation(it.key, it.value) })
        return ProfileUpdateCommand(displayName!!, version!!)
    }

    private fun displayName(value: Any?): String? {
        val trimmed = (value as? String)?.trim() ?: return null
        val length = trimmed.codePointCount(0, trimmed.length)
        if (length !in 1..MAX_DISPLAY_NAME) return null
        if (trimmed.codePoints().anyMatch { Character.getType(it) == Character.CONTROL.toInt() }) return null
        return trimmed
    }

    private fun version(value: Any?): Long? = when (value) {
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }?.takeIf { it >= 0 }

    private companion object {
        const val DISPLAY_NAME = "displayName"
        const val VERSION = "version"
        const val MAX_DISPLAY_NAME = 255
        const val INVALID = "Invalid value"
        const val NOT_EDITABLE = "Field is not editable"
        val EDITABLE = setOf(DISPLAY_NAME, VERSION)
    }
}
