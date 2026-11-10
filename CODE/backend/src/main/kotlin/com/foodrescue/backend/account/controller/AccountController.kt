// AC-08,10,11: AccountController responsibility.
package com.foodrescue.backend.account.controller

import com.foodrescue.backend.account.repository.*
import com.foodrescue.backend.account.dto.AccountProfileResponse
import com.foodrescue.backend.common.security.*
import com.foodrescue.backend.common.exception.authenticationFailed
import org.springframework.web.bind.annotation.*
import org.springframework.transaction.annotation.Transactional
@RestController
@RequestMapping("/api/v1/accounts")
class AccountController(private val accounts: AccountRepository,private val roles: AccountRoleRepository,private val stores: StoreAuthorizationService,private val current: CurrentAccount) {
    @GetMapping("/me") @Transactional(readOnly=true)
    fun me(): AccountProfileResponse {
        val id=current.get().accountId; val a=accounts.findById(id).orElse(null) ?: authenticationFailed()
        return AccountProfileResponse(id,a.loginIdentifier,a.displayName,roles.findAllByAccountId(id).map { it.role.name },stores.contexts(id))
    }
}
