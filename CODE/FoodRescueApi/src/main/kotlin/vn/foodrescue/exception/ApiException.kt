package vn.foodrescue.exception

import org.springframework.http.HttpStatus

class ApiException(val status: HttpStatus, val code: String, override val message: String,
    val retryAfterSeconds: Long? = null) : RuntimeException(message)
