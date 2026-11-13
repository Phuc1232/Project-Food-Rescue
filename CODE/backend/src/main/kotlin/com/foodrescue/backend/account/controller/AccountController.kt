// AC-08,10,11 (T2.1 AC-01..09): AccountController responsibility.
package com.foodrescue.backend.account.controller

import com.foodrescue.backend.account.dto.AccountProfileResponse
import com.foodrescue.backend.account.service.AccountProfileService
import com.foodrescue.backend.account.service.ProfileUpdateRequestParser
import com.foodrescue.backend.common.security.CurrentAccount
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController(private val profiles: AccountProfileService,private val parser: ProfileUpdateRequestParser,private val current: CurrentAccount) {
    @GetMapping("/me")
    fun me(): AccountProfileResponse = profiles.get(current.get().accountId)

    @PatchMapping("/me")
    fun updateMe(@RequestBody(required = false) body: Map<String, Any?>?): AccountProfileResponse =
        profiles.update(current.get().accountId, parser.parse(body))
}
