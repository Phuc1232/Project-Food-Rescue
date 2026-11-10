// AC-04,08,10,19,26: ApiAuthenticationEntryPoint responsibility.
package com.foodrescue.backend.middleware.error

import jakarta.servlet.http.*
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import com.foodrescue.backend.common.exception.ApiException
@Component
class ApiAuthenticationEntryPoint(private val writer: SecurityErrorWriter): AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest,response: HttpServletResponse,ex: AuthenticationException) =
        writer.write(request,response,ApiException(401,"AUTHENTICATION_FAILED","Authentication failed"))
}
