// AC-16,19: AdminBootstrapCommand responsibility.
package com.foodrescue.backend.auth.bootstrap

import com.foodrescue.backend.account.*
import com.foodrescue.backend.account.entity.AccountRole
import com.foodrescue.backend.account.repository.*
import com.foodrescue.backend.auth.EmailIdentifierNormalizer
import com.foodrescue.backend.audit.AuthAuditService
import org.springframework.boot.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
@Component
@ConditionalOnProperty(name=["auth.bootstrap-admin"],havingValue="true")
class AdminBootstrapCommand(private val accounts: AccountRepository,private val roles: AccountRoleRepository,private val normalizer: EmailIdentifierNormalizer,private val audit: AuthAuditService): ApplicationRunner {
    @Transactional override fun run(args: ApplicationArguments) {
        val email=args.getOptionValues("admin-email")?.singleOrNull() ?: error("Exactly one --admin-email is required")
        val a=accounts.findEmailForUpdate(normalizer.normalize(email)) ?: error("Account not found")
        check(a.status==AccountStatus.ACTIVE && a.emailVerifiedAt!=null) { "Verified active account required" }
        if(!roles.existsByAccountIdAndRole(a.id!!,PlatformRole.ADMIN)) { roles.save(AccountRole(a,PlatformRole.ADMIN)); audit.record(a,"ADMIN_GRANTED") }
    }
}
