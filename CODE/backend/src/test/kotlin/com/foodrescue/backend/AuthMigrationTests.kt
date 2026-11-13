// AC-01..26: AuthMigrationTests responsibility.
package com.foodrescue.backend

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.testcontainers.postgresql.PostgreSQLContainer
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.util.UUID
class AuthMigrationTests {
    @Test fun `upgrade from V3 preserves account and history but revokes legacy session`() {
        PostgreSQLContainer("postgres:16-alpine").use { db ->
            db.start()
            val source=DriverManagerDataSource(db.jdbcUrl,db.username,db.password); val jdbc=JdbcTemplate(source)
            Flyway.configure().dataSource(source).target("3").load().migrate()
            val account=UUID.randomUUID(); val session=UUID.randomUUID()
            jdbc.update("insert into account(id,created_at,updated_at,version,login_identifier,password_hash,display_name,status) values(?,now(),now(),0,'legacy@example.test','legacy-hash','Legacy','ACTIVE')",account)
            jdbc.update("insert into login_session(id,created_at,updated_at,version,account_id,token_hash,expires_at) values(?,now(),now(),0,?,'legacy-token',now()+interval '1 day')",session,account)
            Flyway.configure().dataSource(source).load().migrate()
            assertEquals("legacy-hash",jdbc.queryForObject("select password_hash from account",String::class.java))
            assertEquals(0,jdbc.queryForObject("select count(*) from account where email_verified_at is not null",Int::class.java))
            assertEquals(1,jdbc.queryForObject("select count(*) from login_session where revoked_at is not null",Int::class.java))
            assertEquals(5,jdbc.queryForObject("select count(*) from flyway_schema_history where success",Int::class.java))
        }
    }
}
