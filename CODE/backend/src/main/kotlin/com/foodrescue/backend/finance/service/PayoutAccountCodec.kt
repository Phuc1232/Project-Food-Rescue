// AC-01..12 (T8.4): PayoutAccountCodec responsibility.
package com.foodrescue.backend.finance.service
import com.foodrescue.backend.finance.PayoutAccountProperties
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
@Component
class PayoutAccountCodec(properties: PayoutAccountProperties) {
    private val keyRing=properties.keyRing()
    val activeKeyId=properties.activeKeyId
    private val random=SecureRandom()
    fun encrypt(storeId: UUID, accountVersion: Int, accountNumber: String): String = try {
        val iv=ByteArray(12).also(random::nextBytes);val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,keyRing.getValue(activeKeyId),GCMParameterSpec(128,iv))
        cipher.updateAAD("$storeId:$accountVersion".toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(iv+cipher.doFinal(accountNumber.toByteArray(Charsets.UTF_8)))
    } catch (_: Exception) { throw ApiException(503,"SERVICE_UNAVAILABLE","Payout account encryption unavailable") }
    fun decrypt(storeId: UUID, accountVersion: Int, keyId: String, encrypted: String): String = try {
        val bytes=Base64.getDecoder().decode(encrypted);require(bytes.size>28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,keyRing.getValue(keyId),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        cipher.updateAAD("$storeId:$accountVersion".toByteArray(Charsets.UTF_8))
        String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    } catch (_: Exception) { throw ApiException(503,"SERVICE_UNAVAILABLE","Payout account decryption unavailable") }
    fun mask(accountNumber: String): String = "*".repeat((accountNumber.length-4).coerceAtLeast(2))+accountNumber.takeLast(4)
}
