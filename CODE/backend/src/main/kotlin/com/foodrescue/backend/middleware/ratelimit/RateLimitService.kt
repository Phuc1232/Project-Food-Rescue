// AC-15,25,26: RateLimitService responsibility.
package com.foodrescue.backend.middleware.ratelimit

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.stereotype.Service
import org.springframework.dao.DataAccessException
import com.foodrescue.backend.common.exception.ApiException
@Service
class RateLimitService(private val store: RateLimitStore,private val properties: RateLimitProperties) {
    fun check(policy: RateLimitPolicy,key: String,increment: Boolean = true) {
        val mac=Mac.getInstance("HmacSHA256"); mac.init(SecretKeySpec(properties.secret.toByteArray(),"HmacSHA256"))
        val hashed=mac.doFinal(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val (count,retry)=try { store.count(policy.name,hashed,increment) }
            catch (_: DataAccessException) { throw ApiException(503,"SERVICE_UNAVAILABLE","Service unavailable") }
        if(if(increment) count > policy.quota else count >= policy.quota)
            throw ApiException(429,"RATE_LIMITED","Too many requests",retryAfter=retry)
    }
}
