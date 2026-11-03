package vn.foodrescue

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import vn.foodrescue.service.SystemService

@Tag("integration")
@SpringBootTest
class SchemaIntegrationTest @Autowired constructor(val jdbc: JdbcTemplate, val system: SystemService) {
    @Test fun `required business and authentication tables migrate`() {
        val tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public'", String::class.java)
        org.junit.jupiter.api.Assertions.assertTrue(tables.containsAll(listOf("tai_khoan", "phien_dang_nhap", "don_hang",
            "thanh_toan", "ma_xac_minh", "quyen_dat_lai_mat_khau", "gioi_han_xac_thuc")))
        assertEquals("UP", system.status().database)
    }
    @Test fun `foreign keys reject orphan sessions`() {
        assertThrows(DataAccessException::class.java) {
            jdbc.update("INSERT INTO phien_dang_nhap(tai_khoan_id,refresh_token_hash,tao_luc,het_han_luc,nhom_phien) VALUES (-9999,'orphan-test',now(),now()+interval '1 day','orphan-test')")
        }
    }
}
