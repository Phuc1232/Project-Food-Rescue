package vn.foodrescue.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import vn.foodrescue.exception.ApiException
import vn.foodrescue.repository.AuthRepository
import java.time.Clock

@Service
class AuthRateLimiter(private val repository: AuthRepository, private val tokens: TokenService, private val clock: Clock) {
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = [ApiException::class])
    fun check(scope: String, value: String, limit: Int) {
        if (repository.countRequest(tokens.keyedHash("rate:$scope:$value"), clock.instant()) > limit)
            throw ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Bạn đã thử quá nhiều lần. Hãy chờ 10 phút rồi thử lại.", 600)
    }
}
