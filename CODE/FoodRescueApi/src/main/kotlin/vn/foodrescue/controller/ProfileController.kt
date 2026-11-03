package vn.foodrescue.controller

import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import vn.foodrescue.dto.ProfileRequest
import vn.foodrescue.service.AuthPrincipal
import vn.foodrescue.service.AuthService

@RestController
@RequestMapping("/api/v1/me")
class ProfileController(private val service: AuthService) {
    @GetMapping fun me(@AuthenticationPrincipal principal: AuthPrincipal) = service.profile(principal)
    @PatchMapping fun update(@AuthenticationPrincipal principal: AuthPrincipal, @Valid @RequestBody body: ProfileRequest) = service.updateProfile(principal, body)
}
