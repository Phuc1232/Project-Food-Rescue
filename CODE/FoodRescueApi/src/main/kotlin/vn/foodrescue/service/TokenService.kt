package vn.foodrescue.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class TokenService(private val encoder: JwtEncoder, private val clock: Clock,
    @param:Value("\${foodrescue.auth.issuer}") private val issuer: String,
    @Value("\${foodrescue.auth.challenge-key}") secret: String) {
    private val random = SecureRandom()
    private val challengeKey = SecretKeySpec(Base64.getDecoder().decode(secret).also {
        require(it.size >= 32) { "AUTH_CHALLENGE_KEY must be base64 of at least 32 random bytes" }
    }, "HmacSHA256")
    val accessLifetime = 900L
    fun randomToken(): String = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
    fun code(): String = random.nextInt(1_000_000).toString().padStart(6, '0')
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toHex()
    fun keyedHash(value: String): String = Mac.getInstance("HmacSHA256").run {
        init(challengeKey); doFinal(value.toByteArray()).toHex()
    }
    fun matches(expected: String, actual: String) = MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    fun access(accountId: Int, sessionId: Int): String {
        val now = clock.instant()
        val claims = JwtClaimsSet.builder().issuer(issuer).audience(listOf("foodrescue-app"))
            .subject(accountId.toString()).issuedAt(now).expiresAt(now.plusSeconds(accessLifetime))
            .claim("sid", sessionId).build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
