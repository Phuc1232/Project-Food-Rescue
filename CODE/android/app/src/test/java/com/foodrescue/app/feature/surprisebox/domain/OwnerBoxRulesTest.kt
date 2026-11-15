package com.foodrescue.app.feature.surprisebox.domain

import org.junit.Assert.*
import org.junit.Test

class OwnerBoxRulesTest {
    private val template = BoxTemplate("bread", "Hộp bánh cuối ngày", BoxCategory.BAKERY, "Bánh tươi trong ngày", "Sữa, trứng, lúa mì", 120000, 49000)
    private val batch = SaleBatch("sale", template, 10, 1000, 3000, 4000, 5000, BatchControl.PUBLISHED)
    private val catalog = OwnerBoxCatalog(listOf(template), listOf(batch))

    @Test fun `valid template saves normalized values and keeps identity`() {
        val result = catalog.saveTemplate(template.toDraft().copy(name = "  Hộp bánh ngon  ", price = "55000"), "unused")
        assertEquals("bread", result.templates.single().id)
        assertEquals("Hộp bánh ngon", result.templates.single().name)
        assertEquals(55000L, result.templates.single().price)
        assertEquals(49000L, result.batches.single().template.price)
    }
    @Test fun `zero negative decimal overflow and price at value are rejected`() {
        for (price in listOf("0", "-1", "1.5", "999999999999999999999999", "120000", "120001")) {
            assertTrue(price, BoxField.PRICE in template.toDraft().copy(price = price).validationErrors(emptyList()))
        }
        assertTrue(BoxField.VALUE in template.toDraft().copy(minimumValue = "0").validationErrors(emptyList()))
    }
    @Test fun `duplicate names include inactive and unicode equivalent names`() {
        val archived = template.copy(active = false)
        assertTrue(BoxField.DUPLICATE in template.toDraft().copy(id = null, name = " HỘP   BÁNH CUỐI NGÀY ").validationErrors(listOf(archived)))
        assertTrue(template.toDraft().validationErrors(listOf(template)).isEmpty())
    }
    @Test fun `status follows exact sale boundaries and end overrides paused and sold out`() {
        assertEquals(BatchStatus.SCHEDULED, batch.statusAt(999))
        assertEquals(BatchStatus.SELLING, batch.statusAt(1000))
        assertEquals(BatchStatus.ENDED, batch.statusAt(3000))
        assertEquals(BatchStatus.SOLD_OUT, batch.copy(held = 3, sold = 7).statusAt(2000))
        assertEquals(BatchStatus.ENDED, batch.copy(control = BatchControl.PAUSED).statusAt(3000))
    }
    @Test fun `draft publishes pauses resumes and ends without changing inventory`() {
        var current = catalog.copy(batches = listOf(batch.copy(control = BatchControl.DRAFT, sold = 2)))
        current = current.operate("sale", BatchAction.PUBLISH, 900)
        assertEquals(BatchStatus.SCHEDULED, current.batches.single().statusAt(900))
        current = current.operate("sale", BatchAction.PAUSE, 1200)
        assertEquals(BatchStatus.PAUSED, current.batches.single().statusAt(1200))
        current = current.operate("sale", BatchAction.RESUME, 1300)
        current = current.operate("sale", BatchAction.END, 1400)
        assertEquals(2, current.batches.single().sold)
        assertTrue(current.batches.single().actionsAt(1400).isEmpty())
    }
    @Test fun `cannot resume expired or exhausted sale`() {
        assertFalse(BatchAction.RESUME in batch.copy(control = BatchControl.PAUSED).actionsAt(3000))
        assertFalse(BatchAction.RESUME in batch.copy(control = BatchControl.PAUSED, held = 10).actionsAt(1500))
        assertThrows(IllegalStateException::class.java) { catalog.operate("sale", BatchAction.RESUME, 3000) }
    }
    @Test fun `quantity cannot fall below held and sold and cannot overflow`() {
        val locked = catalog.copy(batches = listOf(batch.copy(held = 2, sold = 3)))
        assertTrue(BoxField.QUANTITY in batch.toDraft().copy(quantity = "4").validationErrors(locked, 1500))
        assertTrue(batch.toDraft().copy(quantity = "5").validationErrors(locked, 1500).isEmpty())
        assertTrue(BoxField.QUANTITY in batch.toDraft().copy(quantity = "999999999999").validationErrors(locked, 1500))
    }
    @Test fun `committed batches lock commercial data and pickup window`() {
        val locked = catalog.copy(batches = listOf(batch.copy(sold = 1)))
        assertTrue(BoxField.LOCKED in batch.toDraft().copy(pickupEnd = 6000).validationErrors(locked, 1500))
        assertTrue(BoxField.LOCKED in batch.toDraft().copy(templateId = "other").validationErrors(locked, 1500))
        assertTrue(batch.toDraft().copy(quantity = "15").validationErrors(locked, 1500).isEmpty())
    }
    @Test fun `invalid sale and pickup windows are rejected`() {
        assertTrue(BoxField.SALES in batch.toDraft().copy(salesEnd = 500).validationErrors(catalog, 500))
        assertTrue(BoxField.PICKUP in batch.toDraft().copy(pickupEnd = 4000).validationErrors(catalog, 500))
        assertTrue(BoxField.PICKUP in batch.toDraft().copy(pickupStart = 2000).validationErrors(catalog, 500))
        assertTrue(BoxField.LOCKED in batch.toDraft().validationErrors(catalog, 3000))
    }
    @Test fun `inactive templates cannot create batches but existing snapshots remain usable`() {
        val inactive = catalog.copy(templates = listOf(template.copy(active = false)))
        assertTrue(BoxField.TEMPLATE in batch.toDraft().copy(id = null).validationErrors(inactive, 500))
        assertTrue(batch.toDraft().copy(quantity = "12").validationErrors(inactive, 1500).isEmpty())
    }
    @Test fun `new batch snapshots template and starts unpublished`() {
        val result = catalog.saveBatch(batch.toDraft().copy(id = null), 500, "new")
        val created = result.batches.last()
        assertEquals("new", created.id)
        assertEquals(10, created.remaining)
        assertEquals(BatchControl.DRAFT, created.control)
        assertEquals(49000L, created.template.price)
    }
    @Test fun `editing quantity retains snapshot after template price changes`() {
        val changed = catalog.saveTemplate(template.toDraft().copy(price = "60000"), "unused")
        val edited = changed.saveBatch(batch.toDraft().copy(quantity = "20"), 1500, "unused")
        assertEquals(49000L, edited.batches.single().template.price)
        assertEquals(20, edited.batches.single().remaining)
    }
    @Test fun `unpublished draft can reschedule after original window expires`() {
        val draftBatch = batch.copy(control = BatchControl.DRAFT)
        val drafts = catalog.copy(batches = listOf(draftBatch))
        assertEquals(BatchStatus.DRAFT, draftBatch.statusAt(6000))
        assertFalse(BatchAction.PUBLISH in draftBatch.actionsAt(6000))
        val rescheduled = drafts.saveBatch(draftBatch.toDraft().copy(salesStart = 7000, salesEnd = 8000, pickupStart = 8000, pickupEnd = 9000), 6000, "unused")
        assertEquals(7000L, rescheduled.batches.single().salesStart)
    }
}
