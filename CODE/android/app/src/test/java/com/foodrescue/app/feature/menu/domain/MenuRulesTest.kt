package com.foodrescue.app.feature.menu.domain

import org.junit.Assert.*
import org.junit.Test

class MenuRulesTest {
    @Test fun pricesMustBePositiveWholeDongAndCannotOverflow() {
        listOf("", "0", "-1000", "1.5", "1000000000", "999999999999999999999999").forEach { price ->
            assertTrue("Invalid price accepted: $price", MenuField.PRICE in MenuDraft(name = "Bánh mì", price = price).validationErrors(emptyList()))
        }
        assertTrue(MenuDraft(name = "Bánh mì", price = "25000").validationErrors(emptyList()).isEmpty())
    }

    private fun item(id: String, name: String, price: Long = 25000, status: MenuStatus = MenuStatus.ACTIVE) =
        MenuItem(id, name, MenuCategory.BAKERY, MenuUnit.PIECE, price, "", status, null, 1, 1)

    @Test fun duplicatesIgnoreCaseAndWhitespaceButKeepVietnameseAccentsAndUnits() {
        val existing = item("a", "Bánh cá")
        assertTrue(MenuField.DUPLICATE in existing.toDraft().copy(id = null, name = "  BÁNH   CÁ  ").validationErrors(listOf(existing)))
        assertTrue(existing.toDraft().copy(name = "BÁNH CÁ").validationErrors(listOf(existing)).isEmpty())
        assertTrue(existing.toDraft().copy(id = null, name = "Bánh cà").validationErrors(listOf(existing)).isEmpty())
        assertTrue(existing.toDraft().copy(id = null, unit = MenuUnit.BOX).validationErrors(listOf(existing)).isEmpty())
    }

    @Test fun searchCombinesCategoryStatusAndPriceSortWithoutChangingCatalog() {
        val items = listOf(item("a", "Bánh mì", 15000), item("b", "Bánh kem", 95000), item("c", "Bánh cuộn", 30000, MenuStatus.INACTIVE))
        assertEquals(listOf("b", "a"), items.filterMenu("BANH", MenuCategory.BAKERY, MenuStatus.ACTIVE, MenuSort.PRICE_DESC).map { it.id })
        assertEquals(listOf("c"), items.filterMenu("cuon", null, null, MenuSort.NAME).map { it.id })
        assertTrue(items.filterMenu("", MenuCategory.DRINK, null, MenuSort.UPDATED).isEmpty())
        assertEquals(3, items.size)
    }

    @Test fun invalidNameAndLongDescriptionAreReportedTogether() {
        assertEquals(setOf(MenuField.NAME, MenuField.DESCRIPTION), MenuDraft(name = "x", price = "1000", description = "a".repeat(501)).validationErrors(emptyList()))
    }
}
