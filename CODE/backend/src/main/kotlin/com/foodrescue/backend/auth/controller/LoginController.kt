// AC-07,08,15: LoginController responsibility.
package com.foodrescue.backend.auth.controller

import com.foodrescue.backend.auth.dto.*
import com.foodrescue.backend.auth.service.LoginService
import com.foodrescue.backend.middleware.request.ClientIpResolver
import jakarta.validation.Valid
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.*
@RestController
@RequestMapping("/api/v1/auth")
class LoginController(private val service: LoginService,private val ips: ClientIpResolver) {
    @PostMapping("/login") fun login(@Valid @RequestBody request: LoginRequest,http: HttpServletRequest)=service.login(request,ips.resolve(http))
}
