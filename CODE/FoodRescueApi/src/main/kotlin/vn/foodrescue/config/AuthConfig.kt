package vn.foodrescue.config

import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.*
import java.time.Clock
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

@Configuration
class AuthConfig {
    @Bean fun clock(): Clock = Clock.systemUTC()
    @Bean fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder(16, 32, 1, 19456, 2)
    @Bean fun signingKey(@Value("\${foodrescue.auth.signing-key}") encoded: String): SecretKeySpec {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size >= 32) { "AUTH_SIGNING_KEY must be base64 of at least 32 random bytes" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }
    @Bean fun jwtEncoder(key: SecretKeySpec): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(key))
    @Bean fun jwtDecoder(key: SecretKeySpec, @Value("\${foodrescue.auth.issuer}") issuer: String): JwtDecoder {
        val decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build()
        val audience = OAuth2TokenValidator<Jwt> { jwt ->
            if (jwt.audience.contains("foodrescue-app")) OAuth2TokenValidatorResult.success()
            else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token"))
        }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(JwtValidators.createDefaultWithIssuer(issuer), audience))
        return decoder
    }
}
