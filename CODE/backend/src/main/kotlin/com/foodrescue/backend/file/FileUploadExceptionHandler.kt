// AC-02,03: FileUploadExceptionHandler responsibility.
package com.foodrescue.backend.file

import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.ApiExceptionHandler
import com.foodrescue.backend.common.response.ApiError
import com.foodrescue.backend.common.response.FieldViolation
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException

/**
 * Maps multipart failures (which the shared handler would turn into 500) to 400, delegating to
 * [ApiExceptionHandler] so the error body stays identical (T3.1 plan, decision P1).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class FileUploadExceptionHandler(private val delegate: ApiExceptionHandler) {
    @ExceptionHandler(MultipartException::class)
    fun handle(ex: MultipartException, request: HttpServletRequest): ResponseEntity<ApiError> = delegate.handle(
        if (ex is MaxUploadSizeExceededException)
            ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("file", "File exceeds the upload limit")))
        else ApiException(400, "INVALID_REQUEST", "Invalid request"), request)
}
