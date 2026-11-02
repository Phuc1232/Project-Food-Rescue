package vn.foodrescue.app

import org.junit.Assert.*
import org.junit.Test
import vn.foodrescue.app.data.model.*
import vn.foodrescue.app.data.sample.SampleBoxes

class BoxSearchTest {
    @Test fun `search handles Vietnamese accents uppercase and surrounding spaces`() {
        assertEquals(listOf("demo-bakery"), SampleBoxes.items.filterBoxes("  TIEM BANH MOC  ", FoodCategory.ALL).map { it.id })
    }
    @Test fun `category and search are combined`() {
        assertTrue(SampleBoxes.items.filterBoxes("banh", FoodCategory.MEAL).isEmpty())
        assertEquals(1, SampleBoxes.items.filterBoxes("", FoodCategory.MEAL).size)
    }
    @Test fun `unknown query has no matches while blank query restores all`() {
        assertTrue(SampleBoxes.items.filterBoxes("khong-co-cua-hang-nay", FoodCategory.ALL).isEmpty())
        assertEquals(3, SampleBoxes.items.filterBoxes(" ", FoodCategory.ALL).size)
    }
    @Test fun `price uses Vietnamese separators and dong suffix`() {
        assertEquals("39.000 đ", 39000L.vnd())
    }
}
