// AC-25,26: RequestIdFilter responsibility.
package com.foodrescue.backend.middleware.request

import jakarta.servlet.FilterChain
import jakarta.servlet.http.*
import org.slf4j.MDC
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest,response: HttpServletResponse,chain: FilterChain) {
        val id=UUID.randomUUID().toString()
        request.setAttribute("requestId",id); response.setHeader("X-Request-ID",id); MDC.put("requestId",id)
        try { chain.doFilter(request,response) } finally { MDC.remove("requestId") }
    }
}
