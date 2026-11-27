// AC-01 (T8.4): Authenticated encryption binds a recipient to its store and account version.
package com.foodrescue.backend
import com.foodrescue.backend.finance.PayoutAccountProperties
import com.foodrescue.backend.finance.service.PayoutAccountCodec
import com.foodrescue.backend.common.exception.ApiException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.util.Base64
class PayoutAccountCodecTests {
    @Test fun `recipient ciphertext binds store version and key and uses random nonces`() {
        val properties=PayoutAccountProperties().apply {activeKeyId="test";keys="test:"+Base64.getEncoder().encodeToString(ByteArray(32) { 19 })}
        val codec=PayoutAccountCodec(properties);val store=UUID.randomUUID();val first=codec.encrypt(store,1,"1234567890")
        assertNotEquals(first,codec.encrypt(store,1,"1234567890"));assertEquals("1234567890",codec.decrypt(store,1,"test",first));assertEquals("******7890",codec.mask("1234567890"))
        assertThrows(ApiException::class.java) {codec.decrypt(UUID.randomUUID(),1,"test",first)}
        assertThrows(ApiException::class.java) {codec.decrypt(store,2,"test",first)}
        assertThrows(ApiException::class.java) {codec.decrypt(store,1,"unknown",first)}
        val damaged=Base64.getDecoder().decode(first).apply {this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte()}
        assertThrows(ApiException::class.java) {codec.decrypt(store,1,"test",Base64.getEncoder().encodeToString(damaged))}
    }
}
