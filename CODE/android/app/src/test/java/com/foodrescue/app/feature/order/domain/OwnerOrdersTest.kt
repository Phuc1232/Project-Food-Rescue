package com.foodrescue.app.feature.order.domain

import com.foodrescue.app.feature.payment.domain.*
import com.foodrescue.app.feature.surprisebox.domain.*
import org.junit.Assert.*
import org.junit.Test

class OwnerOrdersTest {
    private val now = 1_800_000_000_000L
    private fun box() = CatalogBox(-1, "Cửa hàng A", BoxCategory.BAKERY, "Hộp bánh", 35000, 90000, 2,
        "18:00 – 20:00", "Bánh trong ngày", "Sữa", "Bàn Cờ", 0, 18, "A", storeId = -10000,
        batchId = "batch-a", salesStartMillis = now - 1000, salesEndMillis = now + 600000,
        pickupStartMillis = now + 600000, pickupEndMillis = now + 7200000)
    private fun order(paid: Boolean = true) = BoxReservation("order-a", -1, 1, PickupWindow(now + 600000, now + 7200000),
        now - 1000, now + 100000, 35000,
        if (paid) QrPayment("FR123", 35000, PaymentStatus.SUCCEEDED, now) else null, boxSnapshot = box())

    @Test fun `request keeps money and stock pending but suppresses pickup code`() {
        val original = order()
        val result = original.requestOwnerCancellation(OwnerCancellationDraft(original.id, OwnerCancellationReason.OUT_OF_STOCK), now)
        assertNotNull(result.ownerCancellation)
        assertNull(result.cancelledAtMillis)
        assertNull(result.refund)
        assertTrue(result.isPaid)
        assertTrue(result.occupiesStock(now))
        assertNull(result.pickupCode(now))
    }
    @Test fun `unpaid collected and cancelled orders cannot request`() {
        listOf(order(false), order().copy(collectedAtMillis = now), order().copy(cancelledAtMillis = now)).forEach {
            assertEquals(OwnerCancellationIssue.NOT_ELIGIBLE, ownerCancellationIssue(it,
                OwnerCancellationDraft(it.id, OwnerCancellationReason.FOOD_QUALITY)))
        }
    }
    @Test fun `reason and other explanation are mandatory`() {
        assertEquals(OwnerCancellationIssue.REASON_REQUIRED, ownerCancellationIssue(order(), OwnerCancellationDraft("order-a")))
        assertEquals(OwnerCancellationIssue.NOTE_REQUIRED, ownerCancellationIssue(order(),
            OwnerCancellationDraft("order-a", OwnerCancellationReason.OTHER, " ")))
    }
    @Test fun `duplicate requests cannot replace the original audit record`() {
        val requested = order().copy(ownerCancellation = OwnerCancellationRequest(OwnerCancellationReason.OUT_OF_STOCK, "", now))
        assertEquals(OwnerCancellationIssue.ALREADY_REQUESTED, ownerCancellationIssue(requested,
            OwnerCancellationDraft("order-a", OwnerCancellationReason.STORE_CLOSED)))
    }
    @Test fun `fixed batch keeps exact pickup date and minutes`() {
        assertEquals(PickupWindow(now + 600000, now + 7200000), pickupWindowFor(box(), now))
    }
    @Test fun `published availability already includes all accounts reservations`() {
        assertEquals(2, availableQuantity(box(), order().pickup, listOf(order()), now))
    }
    @Test fun `ended sale does not roll over to tomorrow`() {
        val ended = box().copy(salesEndMillis = now)
        assertEquals(BookingIssue.BOX_UNAVAILABLE, bookingIssue(BookingDraft(-1, 1, order().pickup, true), ended, emptyList(), now))
    }
    @Test fun `owner filters distinguish overdue from waiting and search without accents`() {
        val overdue = order().copy(id = "overdue", pickup = PickupWindow(now - 900, now - 100),
            expiresAtMillis = now - 900, createdAtMillis = now - 1000)
        assertEquals(listOf(overdue), selectOwnerOrders(listOf(order(), overdue), "banh", OwnerOrderFilter.OVERDUE, OwnerPickupFilter.ALL, now))
        assertEquals(listOf(order()), selectOwnerOrders(listOf(order(), overdue), "", OwnerOrderFilter.AWAITING_PICKUP, OwnerPickupFilter.ALL, now))
    }
}
