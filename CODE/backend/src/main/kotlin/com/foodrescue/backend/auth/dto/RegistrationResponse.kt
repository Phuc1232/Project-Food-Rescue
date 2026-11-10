// AC-05,06,16,17,19,23: RegistrationResponse responsibility.
package com.foodrescue.backend.auth.dto

import java.util.UUID
data class RegistrationResponse(val accountId: UUID, val status: String)
