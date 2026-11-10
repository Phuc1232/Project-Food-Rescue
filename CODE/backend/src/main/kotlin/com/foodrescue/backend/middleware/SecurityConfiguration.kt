// AC-04,08,10,19,26: SecurityConfiguration responsibility.
package com.foodrescue.backend.middleware

import com.foodrescue.backend.common.security.jwt.SessionJwtAuthenticationConverter
import com.foodrescue.backend.middleware.error.*
import com.foodrescue.backend.middleware.request.*
import com.foodrescue.backend.middleware.ratelimit.*
import org.springframework.context.annotation.*
import org.springframework.core.env.Environment
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.*
@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
@EnableMethodSecurity
class SecurityConfiguration {
    @Bean fun chain(http: HttpSecurity,converter: SessionJwtAuthenticationConverter,entry: ApiAuthenticationEntryPoint,
        denied: ApiAccessDeniedHandler,limits: RateLimitService,ips: ClientIpResolver,errors: SecurityErrorWriter,
        mapper: tools.jackson.databind.ObjectMapper,env: Environment,@Value("\${auth.cors-origins:}") origins: String): SecurityFilterChain {
        val cors=CorsConfiguration().apply {
            allowedOrigins=origins.split(',').filter { it.isNotBlank() }; allowedMethods=listOf("GET","POST","DELETE","OPTIONS")
            allowedHeaders=listOf("Authorization","Content-Type"); exposedHeaders=listOf("X-Request-ID","Retry-After"); allowCredentials=false
        }
        val source=UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",cors)
        val corsFilter=org.springframework.web.filter.CorsFilter(source)
        corsFilter.setCorsProcessor(object: DefaultCorsProcessor() {
            override fun rejectRequest(response: org.springframework.http.server.ServerHttpResponse) {
                response.setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN)
                response.headers.contentType=org.springframework.http.MediaType.APPLICATION_JSON
                mapper.writeValue(response.body,com.foodrescue.backend.common.response.ApiError(
                    "ACCESS_DENIED","Access denied",org.slf4j.MDC.get("requestId") ?: "unavailable"))
                response.flush()
            }
        })
        http.addFilterAt(corsFilter,org.springframework.web.filter.CorsFilter::class.java)
        http.csrf { it.disable() }.cors { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .requestCache { it.disable() }.formLogin { it.disable() }.httpBasic { it.disable() }.logout { it.disable() }
            .exceptionHandling { it.authenticationEntryPoint(entry).accessDeniedHandler(denied) }
            .oauth2ResourceServer { it.authenticationEntryPoint(entry).accessDeniedHandler(denied).jwt { j -> j.jwtAuthenticationConverter(converter) } }
            .authorizeHttpRequests {
                it.requestMatchers("/actuator/health/liveness","/actuator/health/readiness").permitAll()
                // SW-04: production wins even if dev/test is accidentally enabled too.
                if("prod" !in env.activeProfiles && env.activeProfiles.any { p -> p=="dev" || p=="test" }) {
                    val docs = arrayOf("/docs", "/docs/", "/docs/assets/*", "/openapi.yaml")
                    it.requestMatchers(org.springframework.http.HttpMethod.GET, *docs).permitAll()
                    it.requestMatchers(org.springframework.http.HttpMethod.HEAD, *docs).permitAll()
                }
                it.requestMatchers(org.springframework.http.HttpMethod.POST,
                    "/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/refresh","/api/v1/auth/verify-email",
                    "/api/v1/auth/resend-verification","/api/v1/auth/forgot-password","/api/v1/auth/reset-password").permitAll()
                it.requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll()
            }
        // Construct filters here to avoid servlet auto-registration.
        http.addFilterBefore(RateLimitFilter(limits,ips,errors),UsernamePasswordAuthenticationFilter::class.java)
        http.addFilterBefore(RequestIdFilter(),org.springframework.web.filter.CorsFilter::class.java)
        return http.build()
    }
}
