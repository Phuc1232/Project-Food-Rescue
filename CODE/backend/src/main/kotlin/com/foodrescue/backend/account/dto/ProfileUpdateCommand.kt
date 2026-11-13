// AC-01,03,04: ProfileUpdateCommand responsibility.
package com.foodrescue.backend.account.dto

/** Validated, normalized profile change: the only editable field plus the version the client last read. */
data class ProfileUpdateCommand(val displayName: String, val version: Long)
