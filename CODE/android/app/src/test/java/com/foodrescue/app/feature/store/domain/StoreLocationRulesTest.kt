package com.foodrescue.app.feature.store.domain

import org.junit.Assert.*
import org.junit.Test

class StoreLocationRulesTest {
    @Test fun anAddressAloneCannotSubmitAStoreWithoutAConfirmedPin() {
        val addressOnly = OwnerStoreProfile("Tiệm bánh", "Tiệm bánh", "Nguyễn An",
            "0901234567", "47 Nguyễn Thượng Hiền", "Bàn Cờ")
        assertTrue("A confirmed map pin is required", addressOnly.validationErrors().isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) { OwnerStore().submit(addressOnly, 123L) }
    }
}
