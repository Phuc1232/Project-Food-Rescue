package com.foodrescue.app.feature.store.domain

import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.feature.store.presentation.*
import org.junit.Assert.*
import org.junit.Test

class StoreApplicationReviewTest {
    private val profile = OwnerStoreProfile("Bánh Mộc", "Tiệm bánh", "Nguyễn An", "0901234567", "47 Nguyễn Thượng Hiền",
        "Bàn Cờ", location = GeoPoint(10.777, 106.699))
    private val pending = OwnerStore().submit(profile, 100)

    @Test fun amendmentAndResubmissionPreserveHistoryAndSubmittedSnapshots() {
        val changes = pending.review(pending.revision, StoreReviewAction.CHANGES_REQUESTED, "  Bổ sung số nhà chính xác  ", "admin", 200)
        assertEquals(StoreReviewStatus.CHANGES_REQUIRED, changes.status)
        assertEquals("Bổ sung số nhà chính xác", changes.reviewNote)
        val resubmitted = changes.submit(profile.copy(address = "49 Nguyễn Thượng Hiền"), 300)
        assertEquals(StoreReviewStatus.PENDING, resubmitted.status)
        assertEquals("", resubmitted.reviewNote)
        assertEquals(3, resubmitted.reviewHistory.size)
        assertEquals(profile.address, resubmitted.reviewHistory.first().submittedProfile!!.address)
        assertEquals("49 Nguyễn Thượng Hiền", resubmitted.reviewHistory.last().submittedProfile!!.address)
        assertEquals(3L, resubmitted.revision)
    }
    @Test fun approvalPreservesProfileAndPayoutAndAddsActor() {
        val bank = StorePayoutAccount("ACB", "NGUYEN AN", "0123456789")
        val approved = pending.copy(payout = bank).review(1, StoreReviewAction.APPROVED, "", "admin", 200)
        assertEquals(StoreReviewStatus.APPROVED, approved.status)
        assertEquals(bank, approved.payout)
        assertEquals(profile, approved.profile)
        assertEquals("admin", approved.reviewHistory.last().actor)
    }
    @Test fun duplicateAndStaleDecisionCannotOverwriteNewerState() {
        assertThrows(StaleStoreApplication::class.java) { pending.review(0, StoreReviewAction.APPROVED, "", "admin", 200) }
        val approved = pending.review(1, StoreReviewAction.APPROVED, "", "admin", 200)
        assertThrows(StaleStoreApplication::class.java) { approved.review(2, StoreReviewAction.CHANGES_REQUESTED, "Bổ sung địa chỉ", "admin", 300) }
    }
    @Test fun newSubmissionInvalidatesOldDecisionEvenIfStillPending() {
        val next = pending.review(1, StoreReviewAction.CHANGES_REQUESTED, "Bổ sung địa chỉ", "admin", 200).submit(profile, 300)
        assertThrows(StaleStoreApplication::class.java) { next.review(1, StoreReviewAction.APPROVED, "", "admin", 400) }
    }
    @Test fun changesRequireSpecificReasonAndNoInvalidControlCharacters() {
        listOf("", "   ", "abc", "a".repeat(1001), "Bổ sung\u0000địa chỉ").forEach { note ->
            assertThrows(IllegalArgumentException::class.java) { pending.review(1, StoreReviewAction.CHANGES_REQUESTED, note, "admin", 200) }
        }
        assertTrue(validReviewNote(StoreReviewAction.CHANGES_REQUESTED, "Bổ sung số nhà\nKiểm tra vị trí"))
    }
    @Test fun invalidLegacyPendingProfileCanBeReturnedButCannotBeApproved() {
        val incomplete = pending.copy(profile = profile.copy(location = null))
        assertThrows(IllegalArgumentException::class.java) { incomplete.review(1, StoreReviewAction.APPROVED, "", "admin", 200) }
        assertEquals(StoreReviewStatus.CHANGES_REQUIRED,
            incomplete.review(1, StoreReviewAction.CHANGES_REQUESTED, "Bổ sung ghim vị trí cửa hàng", "admin", 200).status)
    }
    @Test fun draftCannotBeReviewedAndAdminCannotSubmitUsingDecisionEndpoint() {
        assertThrows(StaleStoreApplication::class.java) { OwnerStore().review(0, StoreReviewAction.APPROVED, "", "admin", 100) }
        assertThrows(IllegalArgumentException::class.java) { pending.review(1, StoreReviewAction.SUBMITTED, "", "admin", 100) }
    }
    @Test fun searchMatchesVietnameseWithoutAccentsAndComposesWithStatusFilter() {
        val list = listOf(StoreApplication("a", -10000, pending), StoreApplication("b", -10001, pending.copy(status = StoreReviewStatus.APPROVED)))
        val state = StoreApplicationsState(applications = list, query = "banh moc", loading = false)
        assertEquals(listOf("a"), state.visible.map { it.accountKey })
        assertEquals(2, state.copy(filter = ApplicationFilter.ALL).visible.size)
        assertEquals(1, state.copy(query = "HS-10000").visible.size)
        assertEquals(0, state.copy(query = "khong co").visible.size)
        assertEquals(1, state.count(StoreReviewStatus.APPROVED))
    }
}
