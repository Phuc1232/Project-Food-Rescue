// AC-13 (T7.1): PickupCodeProperties holds environment supplied pickup encryption keys.
package com.foodrescue.backend.pickup

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

@Component
@ConfigurationProperties("pickup.code")
class PickupCodeProperties {
    var activeKeyId: String = ""
    var keys: String = ""

    fun keyRing(): Map<String, SecretKeySpec> {
        val entries = keys.split(',').map { it.trim().split(':', limit = 2) }
        require(activeKeyId.matches(Regex("[A-Za-z0-9_-]{1,64}")) && entries.all { it.size == 2 }) { "Invalid pickup code key configuration" }
        val result = linkedMapOf<String, SecretKeySpec>()
        for ((id, encoded) in entries) {
            val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) { null }
            require(id.matches(Regex("[A-Za-z0-9_-]{1,64}")) && bytes?.size == 32 && id !in result) { "Invalid pickup code key configuration" }
            result[id] = SecretKeySpec(bytes, "AES")
        }
        require(activeKeyId in result) { "Invalid pickup code key configuration" }
        return result
    }
}
