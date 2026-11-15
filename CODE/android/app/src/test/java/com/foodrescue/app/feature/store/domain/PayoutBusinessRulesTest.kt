package com.foodrescue.app.feature.store.domain

import org.junit.Assert.*
import org.junit.Test
import com.foodrescue.app.feature.store.data.LocalPayoutBanks
import com.foodrescue.app.core.common.GeoPoint

class PayoutBusinessRulesTest {
    @Test fun businessAccountHolderCanContainDigitsAndCompanyPunctuation() {
        val account = StorePayoutAccount("Vietcombank", "CONG TY TNHH BANH 365 & CO.", "00123456789")
        assertTrue(account.validationErrors(LocalPayoutBanks.all).isEmpty())
    }

    @Test fun catalogSupportsLegacyNamesAndVietnameseSearch() {
        val banks = LocalPayoutBanks.all
        assertEquals(47, banks.size)
        assertEquals(banks.size, banks.map { it.code }.toSet().size)
        assertEquals("MB", banks.findBank("MB")?.code)
        assertEquals("MB", banks.findBank("MBBank")?.code)
        assertEquals("VCCB", banks.findBank("VietCapitalBank")?.code)
        assertEquals("BVBank", banks.findBank("VCCB")?.name)
        assertEquals("VCBNeo", banks.findBank("CBB")?.name)
        assertTrue(banks.findBank("VCB")!!.matchesQuery("ngoai thuong"))
        assertTrue(banks.findBank("ICB")!!.matchesQuery("vietinbank"))
        assertNull(banks.findBank("MoMo"))
    }

    @Test fun unknownBankAndNumericHolderAreRejectedWithoutChangingSavedAccount() {
        val saved = StorePayoutAccount("ACB", "NGUYEN AN", "00123456789")
        val store = OwnerStore(status = StoreReviewStatus.APPROVED, payout = saved)
        val invalid = saved.copy(bank = "Unknown", holder = "12345")
        assertEquals(setOf(PayoutField.BANK, PayoutField.HOLDER), invalid.validationErrors(LocalPayoutBanks.all))
        assertThrows(IllegalArgumentException::class.java) { store.updatePayout(invalid, LocalPayoutBanks.all) }
        assertEquals(saved, store.payout)
        assertEquals("00123456789", store.updatePayout(saved.copy(bank = "acb"), LocalPayoutBanks.all).payout?.number)
    }

    @Test fun businessEmailIsOptionalAndValidatedSeparately() {
        val profile = OwnerStoreProfile("Tiệm Bánh", "Tiệm bánh", "Nguyễn An", "0901234567",
            "47 Nguyễn Thượng Hiền", "Bàn Cờ", location = GeoPoint(10.777, 106.699))
        assertTrue(profile.validationErrors().isEmpty())
        assertTrue(profile.copy(contactEmail = " shop@example.com ").validationErrors().isEmpty())
        assertEquals(setOf(StoreField.EMAIL), profile.copy(contactEmail = "missing-domain@").validationErrors())
        assertEquals("shop@example.com", profile.copy(contactEmail = " shop@example.com ").normalized().contactEmail)
    }
}
