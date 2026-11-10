// AC-06,13,20,24: EmailIdentifierNormalizer responsibility.
package com.foodrescue.backend.auth

import java.util.Locale
import org.springframework.stereotype.Component
import com.foodrescue.backend.common.exception.ApiException
@Component
class EmailIdentifierNormalizer {
    fun normalize(value: String): String {
        val email=value.trim().lowercase(Locale.ROOT)
        if(email.length > 255 || !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email))
            throw ApiException(400,"INVALID_EMAIL","Invalid email")
        return email
    }
}
