// AC-15,25,26: RateLimitFilter responsibility.
package com.foodrescue.backend.middleware.ratelimit

import jakarta.servlet.*
import jakarta.servlet.http.*
import org.springframework.web.filter.OncePerRequestFilter
import com.foodrescue.backend.middleware.request.ClientIpResolver
import com.foodrescue.backend.middleware.error.SecurityErrorWriter
import com.foodrescue.backend.common.exception.ApiException
import java.io.ByteArrayInputStream
class RateLimitFilter(private val limits: RateLimitService,private val ips: ClientIpResolver,private val errors: SecurityErrorWriter): OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest,response: HttpServletResponse,chain: FilterChain) {
        // SW-03,06: match API paths independently of the servlet context prefix.
        val path=request.requestURI.removePrefix(request.contextPath)
        if(!path.startsWith("/api/v1/")) { chain.doFilter(request,response); return }
        try {
            val policy=when(path.removePrefix("/api/v1/auth/")) {
                "register" -> RateLimitPolicy.REGISTER_IP
                "login" -> RateLimitPolicy.LOGIN_IP
                "refresh" -> RateLimitPolicy.REFRESH_IP
                "forgot-password" -> RateLimitPolicy.FORGOT_IP
                "resend-verification" -> RateLimitPolicy.RESEND_IP
                "verify-email","reset-password" -> RateLimitPolicy.ACTION_IP
                else -> null
            }
            if(policy!=null) limits.check(policy,ips.resolve(request))
            // T3.1 Q1: file uploads stream to Tomcat temp files; spring.servlet.multipart caps their size instead.
            if(isFileUpload(request,path)) { chain.doFilter(request,response); return }
            if(request.contentLengthLong>16384) throw ApiException(413,"REQUEST_TOO_LARGE","Request too large")
            // Bound chunked bodies as well, before JSON parsing.
            val bytes=request.inputStream.readNBytes(16385)
            if(bytes.size>16384) throw ApiException(413,"REQUEST_TOO_LARGE","Request too large")
            val wrapped=object: HttpServletRequestWrapper(request) {
                override fun getInputStream(): ServletInputStream {
                    val input=ByteArrayInputStream(bytes)
                    return object: ServletInputStream() {
                        override fun read()=input.read()
                        override fun isFinished()=input.available()==0
                        override fun isReady()=true
                        override fun setReadListener(listener: ReadListener) { throw UnsupportedOperationException() }
                    }
                }
                override fun getReader()=getInputStream().bufferedReader(Charsets.UTF_8)
            }
            chain.doFilter(wrapped,response)
        } catch(e: ApiException) { errors.write(request,response,e) }
    }
    private fun isFileUpload(request: HttpServletRequest,path: String)=
        request.method=="POST" && path=="/api/v1/files" &&
            request.contentType?.lowercase()?.startsWith("multipart/form-data")==true
}
