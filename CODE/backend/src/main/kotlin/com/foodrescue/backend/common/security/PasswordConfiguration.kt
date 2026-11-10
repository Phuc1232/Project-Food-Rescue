// AC-06,12,13,14,17,24: PasswordConfiguration responsibility.
package com.foodrescue.backend.common.security

import org.springframework.context.annotation.*
import org.springframework.security.crypto.password.*
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
@Configuration
class PasswordConfiguration {
    @Bean fun passwordEncoder(): PasswordEncoder = DelegatingPasswordEncoder("argon2id",mapOf("argon2id" to Argon2PasswordEncoder(16,32,1,19456,2)))
}

// Unicode code points, not UTF-16 units; never trim or truncate passwords.
fun validatePassword(password: String) {
    if(password.codePointCount(0,password.length) !in 12..128)
        throw com.foodrescue.backend.common.exception.ApiException(400,"INVALID_PASSWORD","Password must contain 12 to 128 Unicode characters")
}
fun safeMatches(encoder: PasswordEncoder,raw: String,hash: String): Boolean =
    try { encoder.matches(raw,hash) } catch (_: IllegalArgumentException) { false }
