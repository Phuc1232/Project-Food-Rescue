// AC-07,08,11,20,21: JwtConfiguration responsibility.
package com.foodrescue.backend.common.security.jwt

import org.springframework.context.annotation.*
import org.springframework.core.io.ResourceLoader
import org.springframework.security.converter.RsaKeyConverters
import org.springframework.security.oauth2.jwt.*
import org.springframework.security.oauth2.core.*
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import java.time.*
import java.util.UUID
@Configuration
class JwtConfiguration {
    @Bean fun jwtEncoder(p: JwtProperties, resources: ResourceLoader): JwtEncoder {
        require(p.keyId.isNotBlank() && !p.accessTtl.isNegative && !p.accessTtl.isZero)
        val public=resources.getResource(p.publicKeyLocation).inputStream.use { RsaKeyConverters.x509().convert(it) }
        val private=resources.getResource(p.privateKeyLocation).inputStream.use { RsaKeyConverters.pkcs8().convert(it) }
        require(public.modulus.bitLength()>=2048 && public.modulus==private.modulus) { "RSA keys must match and be >= 2048 bits" }
        return NimbusJwtEncoder(ImmutableJWKSet(JWKSet(RSAKey.Builder(public).privateKey(private).keyID(p.keyId).build())))
    }
    @Bean fun jwtDecoder(p: JwtProperties,resources: ResourceLoader,clock: Clock): JwtDecoder {
        val key=resources.getResource(p.publicKeyLocation).inputStream.use { RsaKeyConverters.x509().convert(it) }
        val decoder=NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build()
        val times=JwtTimestampValidator(Duration.ofSeconds(30)); times.setClock(clock)
        val claims=OAuth2TokenValidator<Jwt> { jwt ->
            val ok=try {
                UUID.fromString(jwt.subject); UUID.fromString(jwt.getClaimAsString("sid")); UUID.fromString(jwt.id)
                jwt.audience?.contains(p.audience)==true && jwt.issuedAt!=null && jwt.notBefore!=null && jwt.expiresAt!=null &&
                    !jwt.issuedAt!!.isAfter(clock.instant().plusSeconds(30)) && jwt.expiresAt!!.isAfter(jwt.issuedAt) &&
                    jwt.expiresAt!!.epochSecond-jwt.issuedAt!!.epochSecond <= p.accessTtl.seconds
            } catch (_: Exception) { false }
            if(ok) OAuth2TokenValidatorResult.success() else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token"))
        }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(times,JwtIssuerValidator(p.issuer),claims))
        return decoder
    }
}
