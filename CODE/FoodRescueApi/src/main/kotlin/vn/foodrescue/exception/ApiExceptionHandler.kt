package vn.foodrescue.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mail.MailException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun business(error: ApiException): ResponseEntity<ApiError> {
        val response = ResponseEntity.status(error.status)
        error.retryAfterSeconds?.let { response.header("Retry-After", it.toString()) }
        return response.body(ApiError(error.code, error.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(error: MethodArgumentNotValidException) = ResponseEntity.badRequest().body(
        ApiError("VALIDATION_ERROR", "Kiểm tra lại thông tin đã nhập.",
            error.bindingResult.fieldErrors.associate { it.field to "Giá trị chưa hợp lệ." }))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun malformed() = ResponseEntity.badRequest().body(ApiError("INVALID_REQUEST", "Nội dung yêu cầu chưa hợp lệ."))

    @ExceptionHandler(MailException::class)
    fun mailUnavailable(error: MailException): ResponseEntity<ApiError> {
        val body = ApiError("MAIL_UNAVAILABLE", "Chưa gửi được email. Vui lòng thử lại sau.")
        // SMTP exceptions can contain addresses, server responses and credentials; log only their class.
        log.error("Email delivery failure trace={} type={}", body.traceId, error.javaClass.simpleName)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body)
    }

    @ExceptionHandler(DataAccessException::class)
    fun databaseUnavailable(error: DataAccessException): ResponseEntity<ApiError> {
        val body = ApiError("SERVICE_UNAVAILABLE", "Dịch vụ tạm thời không sẵn sàng. Vui lòng thử lại.")
        // SQL exceptions may contain email addresses or parameter values. Never log their message/stack here.
        log.error("Database failure trace={} type={}", body.traceId, error.javaClass.simpleName)
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body)
    }
}

data class ApiError(val code: String, val message: String, val fieldErrors: Map<String, String> = emptyMap(),
    val traceId: String = UUID.randomUUID().toString())
