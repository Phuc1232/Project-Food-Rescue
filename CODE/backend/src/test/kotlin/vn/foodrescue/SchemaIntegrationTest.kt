package vn.foodrescue

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import vn.foodrescue.system.application.SystemService

@Tag("integration")
@SpringBootTest
class SchemaIntegrationTest @Autowired constructor(val jdbc: JdbcTemplate, val system: SystemService) {
    @Test fun `all 33 business tables migrate`() {
        val count = jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE' AND table_name <> 'flyway_schema_history'", Int::class.java)
        assertEquals(33, count ?: -1)
        assertEquals("UP", system.status().database)
    }
    @Test fun `foreign keys reject orphan sessions`() {
        assertThrows(DataAccessException::class.java) {
            jdbc.update("INSERT INTO phien_dang_nhap(tai_khoan_id,refresh_token_hash,tao_luc,het_han_luc) VALUES (-9999,'orphan-test',now(),now()+interval '1 day')")
        }
    }
}
