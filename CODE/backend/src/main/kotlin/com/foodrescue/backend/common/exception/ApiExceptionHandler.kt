// AC-01,02: ApiExceptionHandler responsibility.
package com.foodrescue.backend.common.exception

import com.foodrescue.backend.common.response.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(Exception::class)
    fun handle(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        val e = when(ex) {
            is ApiException -> ex
            is MethodArgumentNotValidException -> ApiException(400,"VALIDATION_FAILED","Invalid request",
                ex.bindingResult.fieldErrors.map { FieldViolation(it.field, "Invalid value") })
            is HttpMessageNotReadableException, is MethodArgumentTypeMismatchException,
            is ServletRequestBindingException, is HandlerMethodValidationException -> ApiException(400,"INVALID_REQUEST","Invalid request")
            is NoResourceFoundException -> ApiException(404,"NOT_FOUND","Resource not found")
            is HttpRequestMethodNotSupportedException -> ApiException(405,"METHOD_NOT_ALLOWED","Method not allowed")
            is AccessDeniedException -> ApiException(403,"ACCESS_DENIED","Access denied")
            is DataIntegrityViolationException -> ApiException(409,"CONFLICT","Resource already exists or conflicts")
            is DataAccessException -> ApiException(503,"SERVICE_UNAVAILABLE","Service unavailable")
            else -> ApiException(500,"INTERNAL_ERROR","Unexpected server error")
        }
        val builder = ResponseEntity.status(e.status)
        e.retryAfter?.let { builder.header("Retry-After", it.toString()) }
        return builder.body(ApiError(e.code,e.message,request.getAttribute("requestId")?.toString() ?: "unavailable",fieldErrors=e.fields))
    }
}
