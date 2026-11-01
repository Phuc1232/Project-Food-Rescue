package vn.foodrescue.shared.api

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)
    @ExceptionHandler(DataAccessException::class)
    fun databaseUnavailable(error: DataAccessException): ResponseEntity<ApiError> {
        log.error("Database request failed", error)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ApiError("SERVICE_UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng. Vui lòng thử lại."))
    }
}
data class ApiError(val code: String, val message: String)
