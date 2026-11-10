// AC-04,08,10,19,26: ApiAccessDeniedHandler responsibility.
package com.foodrescue.backend.middleware.error

import jakarta.servlet.http.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import com.foodrescue.backend.common.exception.ApiException
@Component
class ApiAccessDeniedHandler(private val writer: SecurityErrorWriter): AccessDeniedHandler {
    override fun handle(request: HttpServletRequest,response: HttpServletResponse,ex: AccessDeniedException) =
        writer.write(request,response,ApiException(403,"ACCESS_DENIED","Access denied"))
}
