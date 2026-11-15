package com.foodrescue.app.feature.store.domain

import org.junit.Assert.*
import org.junit.Test
import com.foodrescue.app.feature.store.data.LocalPayoutBanks

class StoreRulesTest {
    private val profile = OwnerStoreProfile("Tiệm Bánh An Nhiên", "Tiệm bánh", "Nguyễn An",
        "0901234567", "47 Nguyễn Thượng Hiền", "Bàn Cờ", location = com.foodrescue.app.core.common.GeoPoint(10.777, 106.699))
    private val bank = StorePayoutAccount("Vietcombank", "NGUYEN AN", "0123456789")

    @Test fun newApplicationStaysPendingAndNormalizesProfile() {
        val sent = OwnerStore().submit(profile.copy(name = "  Tiệm Bánh An Nhiên  "), 123L)
        assertEquals(StoreReviewStatus.PENDING, sent.status)
        assertEquals(profile.name, sent.profile.name)
        assertEquals(123L, sent.submittedAtMillis)
        assertNull(sent.payout)
    }
    @Test fun incompleteApplicationCannotBeSubmitted() {
        assertTrue(OwnerStoreProfile().validationErrors().containsAll(listOf(StoreField.NAME, StoreField.CONTACT, StoreField.PHONE, StoreField.ADDRESS, StoreField.AREA)))
        assertThrows(IllegalArgumentException::class.java) { OwnerStore().submit(OwnerStoreProfile(), 123L) }
    }
    @Test fun pendingApplicationCannotEditOrChangeBankOrResubmit() {
        val pending = OwnerStore().submit(profile, 123L)
        assertThrows(IllegalStateException::class.java) { pending.updateProfile(profile.copy(name = "Khác")) }
        assertThrows(IllegalStateException::class.java) { pending.updatePayout(bank, LocalPayoutBanks.all) }
        assertThrows(IllegalStateException::class.java) { pending.submit(profile, 456L) }
        assertEquals(123L, pending.submittedAtMillis)
    }
    @Test fun onlySuppliedAmendmentCanBeResubmitted() {
        val rejected = OwnerStore(profile, StoreReviewStatus.CHANGES_REQUIRED, 123L, "Bổ sung địa chỉ")
        val resubmitted = rejected.submit(profile.copy(address = "49 Nguyễn Thượng Hiền"), 456L)
        assertEquals(StoreReviewStatus.PENDING, resubmitted.status)
        assertEquals(456L, resubmitted.submittedAtMillis)
        assertEquals("", resubmitted.reviewNote)
    }
    @Test fun approvedProfileEditPreservesApprovalAndPayout() {
        val updated = OwnerStore(profile, StoreReviewStatus.APPROVED, payout = bank).updateProfile(profile.copy(phone = "+84901234567"))
        assertEquals(StoreReviewStatus.APPROVED, updated.status)
        assertEquals(bank, updated.payout)
        assertEquals("+84901234567", updated.profile.phone)
    }
    @Test fun invalidPhoneAndHoursAreRejectedButOvernightIsSupported() {
        assertTrue(StoreField.PHONE in profile.copy(phone = "09012").validationErrors())
        assertTrue(StoreField.OPEN in profile.copy(opensAt = "25:00").validationErrors())
        assertTrue(StoreField.CLOSE in profile.copy(closesAt = "07:00").validationErrors())
        assertTrue(profile.copy(opensAt = "20:00", closesAt = "02:00").validationErrors().isEmpty())
    }
    @Test fun bankKeepsLeadingZerosAndMasksSummary() {
        val updated = OwnerStore(profile, StoreReviewStatus.APPROVED).updatePayout(bank.copy(holder = " nguyen an "), LocalPayoutBanks.all)
        assertEquals("NGUYEN AN", updated.payout?.holder)
        assertEquals("0123456789", updated.payout?.number)
        assertEquals("•••• 6789", updated.payout?.maskedNumber)
    }
    @Test fun invalidPayoutCannotReplaceExistingAccount() {
        val store = OwnerStore(profile, StoreReviewStatus.APPROVED, payout = bank)
        val invalid = bank.copy(bank = "Unknown", holder = "123", number = "123")
        assertEquals(PayoutField.entries.toSet(), invalid.validationErrors(LocalPayoutBanks.all))
        assertThrows(IllegalArgumentException::class.java) { store.updatePayout(invalid, LocalPayoutBanks.all) }
        assertEquals(bank, store.payout)
    }
}
