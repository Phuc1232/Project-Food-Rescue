package vn.foodrescue.system

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import vn.foodrescue.shared.security.SecurityConfig
import vn.foodrescue.system.api.SystemController
import vn.foodrescue.system.api.SystemStatus
import vn.foodrescue.system.application.SystemService

@WebMvcTest(SystemController::class)
@Import(SecurityConfig::class)
class SystemControllerTest @Autowired constructor(val mvc: MockMvc) {
    @MockitoBean lateinit var service: SystemService
    @Test fun `status returns documented response`() {
        given(service.status()).willReturn(SystemStatus("FoodRescue", "UP", "UP"))
        mvc.get("/api/v1/system/status").andExpect {
            status { isOk() }
            jsonPath("$.service") { value("FoodRescue") }
            jsonPath("$.database") { value("UP") }
        }
    }
    @Test fun `business endpoints are closed until authentication exists`() {
        mvc.get("/api/v1/orders").andExpect { status { isUnauthorized() } }
    }
}
