// AC-04,08,10,19,26: SecurityErrorWriter responsibility.
package com.foodrescue.backend.middleware.error

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.response.ApiError
import tools.jackson.databind.ObjectMapper
import jakarta.servlet.http.*
import org.springframework.stereotype.Component
@Component
class SecurityErrorWriter(private val mapper: ObjectMapper) {
    fun write(request: HttpServletRequest,response: HttpServletResponse,e: ApiException) {
        response.status=e.status; response.contentType="application/json"
        if(e.status==401) response.setHeader("WWW-Authenticate","Bearer")
        e.retryAfter?.let { response.setHeader("Retry-After",it.toString()) }
        mapper.writeValue(response.outputStream,ApiError(e.code,e.message,request.getAttribute("requestId").toString()))
    }
}
