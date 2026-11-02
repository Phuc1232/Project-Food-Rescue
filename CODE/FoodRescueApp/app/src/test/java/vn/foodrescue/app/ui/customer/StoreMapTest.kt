package vn.foodrescue.app.ui.customer

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class StoreMapTest {
    @Test fun `map uses the same query area and filters as the catalog`() {
        val state = DiscoveryState(query = "do uong", district = "Quận 1",
            filters = DiscoveryFilters(price = PriceRange(20000, 40000)))
        assertEquals(listOf(6), mapOffers(state.boxes).map { it.id })
        assertEquals(state.boxes.map { it.id }, mapOffers(state.boxes).map { it.id })
    }

    @Test fun `selection follows filter changes without keeping hidden locations`() {
        val drinks = filterCatalog("", "Quận 1", DiscoveryFilters(category = BoxCategory.DRINKS))
        assertEquals(6, selectedMapOffer(drinks, 1)?.id)
        assertEquals(6, selectedMapOffer(drinks, 6)?.id)
        assertNull(selectedMapOffer(emptyList(), 6))
        assertNull(selectedMapOffer(listOf(demoBoxes.first().copy(location = null)), 1))
    }

    @Test fun `every preview store has a valid and explicitly marked sample location`() {
        assertEquals(demoBoxes.size, mapOffers(demoBoxes).size)
        demoBoxes.forEach {
            val location = requireNotNull(it.location)
            assertTrue(location.isPreview)
            assertTrue(location.address.contains(it.district))
        }
    }

    @Test fun `directions uses coordinate destination regardless of device locale without supplying an origin`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val url = directionsUrl(StoreLocation(10.7694, 106.6989, "Địa chỉ"))
            assertEquals("https://www.google.com/maps/dir/?api=1&destination=10.7694%2C106.6989", url)
            assertFalse(url.contains("origin="))
            assertFalse(url.contains("dir_action=navigate"))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun `invalid coordinates are rejected before creating a pin or directions link`() {
        listOf(Double.NaN to 106.0, 91.0 to 106.0, 10.0 to 181.0, 10.0 to Double.POSITIVE_INFINITY).forEach { (lat, lon) ->
            assertThrows(IllegalArgumentException::class.java) { StoreLocation(lat, lon, "Địa chỉ") }
        }
    }
}
