package vn.foodrescue.app.ui.customer

import org.junit.Assert.*
import org.junit.Test

class DiscoveryCatalogTest {
    @Test fun `search accepts unaccented store and group names but excludes non-searchable details`() {
        assertEquals(listOf(1), searchCatalog("ban mai").map { it.id })
        assertTrue(searchCatalog("lúa mì").isEmpty())
        assertEquals(listOf(1, 4), searchCatalog("banh").map { it.id })
        assertEquals(listOf(6), searchCatalog("do uong").map { it.id })
        assertTrue(searchCatalog("zznomatch").isEmpty())
    }
}
