// AC-01..26: AdminBootstrapTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import com.foodrescue.backend.auth.bootstrap.AdminBootstrapCommand
import com.foodrescue.backend.auth.EmailIdentifierNormalizer
import com.foodrescue.backend.account.repository.*
import com.foodrescue.backend.audit.AuthAuditService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
class AdminBootstrapTests: AuthTestFixtures() {
    @Autowired lateinit var context: ApplicationContext
    @Autowired lateinit var accounts: AccountRepository
    @Autowired lateinit var roles: AccountRoleRepository
    @Autowired lateinit var audit: AuthAuditService
    @Autowired lateinit var normalizer: EmailIdentifierNormalizer
    @Autowired lateinit var tm: PlatformTransactionManager
    @Test fun `bootstrap disabled by default and only verified accounts can be promoted explicitly`() {
        assertTrue(context.getBeansOfType(AdminBootstrapCommand::class.java).isEmpty())
        register(verify=false)
        val command=AdminBootstrapCommand(accounts,roles,normalizer,audit)
        val args=DefaultApplicationArguments("--admin-email=user@example.test")
        assertThrows(IllegalStateException::class.java) { TransactionTemplate(tm).execute { command.run(args) } }
        jdbc.update("update account set status='ACTIVE',email_verified_at=now()")
        repeat(2) { TransactionTemplate(tm).execute { command.run(args) } }
        assertEquals(1,jdbc.queryForObject("select count(*) from account_role where role='ADMIN'",Int::class.java))
    }

    @Test fun `explicit bootstrap starts successfully without web server`() {
        register()
        val keys=listOf("spring.datasource.url","spring.datasource.username","spring.datasource.password",
            "auth.jwt.private-key-location","auth.jwt.public-key-location","auth.jwt.key-id","auth.rate-limit-key-secret")
        val args=keys.map { "--"+it+"="+context.environment.getRequiredProperty(it) } + listOf("--auth.bootstrap-admin=true","--admin-email=user@example.test")
        org.springframework.boot.builder.SpringApplicationBuilder(com.foodrescue.backend.FoodRescueApplication::class.java)
            .web(org.springframework.boot.WebApplicationType.NONE).run(*args.toTypedArray()).use { app ->
                assertEquals(1,app.getBeansOfType(AdminBootstrapCommand::class.java).size)
            }
        assertEquals(1,jdbc.queryForObject("select count(*) from account_role where role='ADMIN'",Int::class.java))
    }
}
