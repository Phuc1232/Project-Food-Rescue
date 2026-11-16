// AC-12 (T3.3): StoreSalesPolicyTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.account.entity.Account
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.store.StoreStatus
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.store.service.StoreSalesPolicy
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal

class StoreSalesPolicyTests {
    private val policy = StoreSalesPolicy()
    private fun store(status: StoreStatus) =
        Store(Mockito.mock(Account::class.java), "S", "A", BigDecimal.ONE, BigDecimal.ONE, "Asia/Ho_Chi_Minh", status)

    @Test fun `only ACTIVE stores may sell or be shown to customers`() {
        for (status in StoreStatus.entries) {
            assertEquals(status == StoreStatus.ACTIVE, policy.isSellable(store(status)), status.name)
            if (status == StoreStatus.ACTIVE) policy.requireSellable(store(status))
            else assertEquals("STORE_NOT_ACTIVE", assertThrows(ApiException::class.java) { policy.requireSellable(store(status)) }.code)
        }
    }
}
