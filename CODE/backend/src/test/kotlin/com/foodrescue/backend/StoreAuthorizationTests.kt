// AC-01..26: StoreAuthorizationTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.support.AuthTestFixtures
import com.foodrescue.backend.common.security.StoreAuthorizationService
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
@org.springframework.context.annotation.Import(StoreAuthorizationTests.ProbeConfiguration::class)
class StoreAuthorizationTests: AuthTestFixtures() {
    @Autowired lateinit var stores: StoreAuthorizationService
    @Test fun `owner and staff permission scoped by store and revoked immediately`() {
        register(); register("staff@example.test")
        val owner=jdbc.queryForObject("select id from account where login_identifier='user@example.test'",UUID::class.java)!!
        val staff=jdbc.queryForObject("select id from account where login_identifier='staff@example.test'",UUID::class.java)!!
        val store=UUID.randomUUID(); val assignment=UUID.randomUUID(); val permission=UUID.randomUUID()
        jdbc.update("""insert into store(id,created_at,updated_at,version,owner_id,name,address,latitude,longitude,timezone,status)
            values(?,now(),now(),0,?,'Test','Address',0,0,'UTC','ACTIVE')""",store,owner)
        jdbc.update("insert into permission(id,created_at,updated_at,version,code,description) values(?,now(),now(),0,'PACK_BOX','Pack')",permission)
        jdbc.update("insert into staff_assignment(id,created_at,updated_at,version,account_id,store_id,status,assigned_at) values(?,now(),now(),0,?,?,'ACTIVE',now())",assignment,staff,store)
        jdbc.update("insert into staff_permission(id,created_at,updated_at,version,assignment_id,permission_id,granted_by_id,granted_at) values(?,now(),now(),0,?,?,?,now())",UUID.randomUUID(),assignment,permission,owner)
        stores.requirePermission(owner,store,"ANY"); stores.requirePermission(staff,store,"PACK_BOX")
        assertEquals(403,assertThrows(ApiException::class.java) { stores.requirePermission(staff,UUID.randomUUID(),"PACK_BOX") }.status)
        assertThrows(ApiException::class.java) { stores.requirePermission(staff,store,"UNKNOWN") }
        jdbc.update("update staff_assignment set status='REVOKED',revoked_at=now()")
        assertThrows(ApiException::class.java) { stores.requirePermission(staff,store,"PACK_BOX") }
        jdbc.update("update account set status='LOCKED' where id=?",owner)
        assertThrows(ApiException::class.java) { stores.requirePermission(owner,store,"ANY") }
    }

    open class AdminProbe {
        @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
        open fun execute() = "allowed"
    }
    @org.springframework.boot.test.context.TestConfiguration
    class ProbeConfiguration {
        @org.springframework.context.annotation.Bean fun adminProbe()=AdminProbe()
    }
    @Autowired lateinit var probe: AdminProbe
    @Autowired lateinit var decoder: org.springframework.security.oauth2.jwt.JwtDecoder
    @Autowired lateinit var converter: com.foodrescue.backend.common.security.jwt.SessionJwtAuthenticationConverter
    @Test fun `platform customer denied admin component and current DB roles apply immediately`() {
        register(); val access=login()["accessToken"].asString()
        val context=org.springframework.security.core.context.SecurityContextHolder.getContext()
        try {
            context.authentication=converter.convert(decoder.decode(access))
            assertThrows(org.springframework.security.access.AccessDeniedException::class.java) { probe.execute() }
            jdbc.update("insert into account_role(id,created_at,updated_at,version,account_id,role) select ?,now(),now(),0,id,'ADMIN' from account",UUID.randomUUID())
            context.authentication=converter.convert(decoder.decode(access)); assertEquals("allowed",probe.execute())
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext() }
    }
}
