// AC-06,13,20,24: CredentialGenerator responsibility.
package com.foodrescue.backend.auth

import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Base64
import org.springframework.stereotype.Component
@Component
class CredentialGenerator {
    private val random = SecureRandom()
    fun generate(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
