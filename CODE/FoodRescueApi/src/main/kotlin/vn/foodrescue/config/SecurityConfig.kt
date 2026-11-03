package vn.foodrescue.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import com.fasterxml.jackson.databind.ObjectMapper
import vn.foodrescue.exception.ApiError
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
class SecurityConfig(private val converter: SessionAuthenticationConverter, private val json: ObjectMapper) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .formLogin { it.disable() }
        .httpBasic { it.disable() }
        .exceptionHandling {
            it.authenticationEntryPoint { _, response, _ ->
                response.status = 401; response.contentType = "application/json;charset=UTF-8"
                json.writeValue(response.writer, ApiError("SESSION_EXPIRED", "Hãy đăng nhập để tiếp tục."))
            }
            it.accessDeniedHandler { _, response, _ ->
                response.status = 403; response.contentType = "application/json;charset=UTF-8"
                json.writeValue(response.writer, ApiError("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này."))
            }
        }
        .oauth2ResourceServer { oauth ->
            oauth.jwt { it.jwtAuthenticationConverter(converter) }
            oauth.authenticationEntryPoint { _, response, _ ->
                response.status = 401; response.contentType = "application/json;charset=UTF-8"
                json.writeValue(response.writer, ApiError("SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."))
            }
        }
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/system/status").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                "/api/v1/auth/email-verifications", "/api/v1/auth/password-reset-requests", "/api/v1/auth/password-resets",
                "/api/v1/auth/challenges/*/verify", "/api/v1/auth/challenges/*/resend").permitAll()
            it.requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
            it.requestMatchers(HttpMethod.GET, "/api/v1/me").hasRole("KHACH_HANG")
            it.requestMatchers(HttpMethod.PATCH, "/api/v1/me").hasRole("KHACH_HANG")
            // Other business routes remain closed until their scoped authorization is implemented.
            it.anyRequest().denyAll()
        }
        .build()
}
