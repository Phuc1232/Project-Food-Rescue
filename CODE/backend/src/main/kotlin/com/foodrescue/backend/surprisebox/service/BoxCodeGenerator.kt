// AC-01 (T4.4): BoxCodeGenerator responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.surprisebox.repository.PhysicalBoxRepository
import org.springframework.stereotype.Component
import java.security.SecureRandom

/** Unique, unguessable box codes "FR-XXXXXXXXXX" (base32 without ambiguous characters, BR-13). */
@Component
class BoxCodeGenerator(private val boxes: PhysicalBoxRepository) {
    private val random = SecureRandom()

    fun next(): String {
        repeat(5) {
            val code = "FR-" + (1..10).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
            if (!boxes.existsByBoxCode(code)) return code
        }
        error("Could not generate a unique box code")
    }

    private companion object { const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }
}
