// AC-08,09,21,22: SessionController responsibility.
package com.foodrescue.backend.auth.controller

import com.foodrescue.backend.auth.dto.*
import com.foodrescue.backend.auth.service.*
import com.foodrescue.backend.common.security.CurrentAccount
import com.foodrescue.backend.common.exception.authenticationFailed
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import org.springframework.http.HttpStatus
import java.util.UUID
@RestController
@RequestMapping("/api/v1/auth")
class SessionController(private val sessions: SessionService,private val refresh: RefreshTokenService,private val current: CurrentAccount) {
    @PostMapping("/refresh") fun refresh(@Valid @RequestBody request: RefreshRequest): TokenPairResponse = refresh.refresh(request.refreshToken) ?: authenticationFailed()
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout() { val p=current.get(); sessions.revoke(p,p.sessionId) }
    @PostMapping("/logout-all") @ResponseStatus(HttpStatus.NO_CONTENT) fun logoutAll()=sessions.logoutAll(current.get())
    @GetMapping("/sessions") fun list(@RequestParam(defaultValue="0") page: Int,@RequestParam(defaultValue="20") size: Int,
        @RequestParam(defaultValue="createdAt") sort: String)=sessions.list(current.get(),page,size,sort)
    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) fun revoke(@PathVariable id: UUID)=sessions.revoke(current.get(),id)
}
