package vn.foodrescue.app.ui.customer

import org.junit.Assert.*
import org.junit.Test

class DiscoveryCatalogTest {
    @Test fun `search accepts unaccented store and group names but excludes non-searchable details`() {
        assertEquals(listOf(1), filterCatalog("ban mai", "Quận 1", DiscoveryFilters()).map { it.id })
        assertTrue(filterCatalog("lúa mì", "TP. Hồ Chí Minh", DiscoveryFilters()).isEmpty())
        val bakery = filterCatalog("banh", "TP. Hồ Chí Minh", DiscoveryFilters())
        assertEquals(listOf(1, 4), bakery.map { it.id })
        assertEquals(listOf(6), filterCatalog("do uong", "Quận 1", DiscoveryFilters()).map { it.id })
    }
    @Test fun `area category price pickup and ordering work together`() {
        val result = filterCatalog("", "Quận 1", DiscoveryFilters(price = PriceRange(0, 39000), pickup = PickupBand.BEFORE_18, sort = CatalogSort.PRICE))
        assertEquals(listOf(6, 3, 1), result.map { it.id })
        assertEquals(listOf(4), filterCatalog("", "Quận 3", DiscoveryFilters()).map { it.id })
        assertTrue(filterCatalog("", "Quận 1", DiscoveryFilters(category = BoxCategory.PRODUCE, pickup = PickupBand.FROM_18)).isEmpty())
    }
    @Test fun `range includes both endpoints and allows an exact price`() {
        val offers = listOf(35000, 49000, 75000).mapIndexed { i, price -> demoBoxes.first().copy(id = i, price = price) }
        fun matches(low: Int, high: Int) = filterCatalog("", "Quận 1", DiscoveryFilters(price = PriceRange(low, high)), offers).map { it.price }
        assertEquals(listOf(35000, 49000, 75000), matches(35000, 75000))
        assertEquals(listOf(49000), matches(35001, 74999))
        assertEquals(listOf(49000), matches(49000, 49000))
        assertTrue(matches(50000, 60000).isEmpty())
    }
    @Test fun `default and full range do not silently cap expensive offers`() {
        val offers = listOf(demoBoxes.first().copy(price = 275000))
        assertEquals(300000, catalogPriceCeiling(offers))
        assertEquals(1, filterCatalog("", "Quận 1", DiscoveryFilters(), offers).size)
        assertNull(selectedPriceRange(0f, 300000f, 300000))
        assertEquals(0, DiscoveryFilters().activeCount)
        assertEquals(2, DiscoveryFilters(category = BoxCategory.DRINKS, price = PriceRange(20000, 80000)).activeCount)
    }
    @Test fun `slider rounds to whole thousand dong and keeps ordered clamped endpoints`() {
        assertEquals(PriceRange(28000, 63000), selectedPriceRange(28300f, 62600f, 100000))
        assertEquals(PriceRange(20000, 70000), selectedPriceRange(70000f, 20000f, 100000))
        assertEquals(PriceRange(49000, 49000), selectedPriceRange(49000f, 49000f, 100000))
        assertNull(selectedPriceRange(-1000f, 101000f, 100000))
    }
    @Test fun `drinks and mixed boxes have separate public categories`() {
        assertEquals(BoxCategory.entries.toSet(), demoBoxes.map { it.category }.toSet())
        assertEquals(listOf(6), filterCatalog("", "Quận 1", DiscoveryFilters(category = BoxCategory.DRINKS)).map { it.id })
        assertEquals(listOf(7), filterCatalog("", "Quận 1", DiscoveryFilters(category = BoxCategory.MIXED)).map { it.id })
    }
}
