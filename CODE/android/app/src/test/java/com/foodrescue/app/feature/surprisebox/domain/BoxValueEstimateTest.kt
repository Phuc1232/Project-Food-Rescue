package com.foodrescue.app.feature.surprisebox.domain

import com.foodrescue.app.feature.menu.domain.*
import org.junit.Assert.*
import org.junit.Test

class BoxValueEstimateTest {
    private fun item(id: String, price: Long, unit: MenuUnit = MenuUnit.PIECE, status: MenuStatus = MenuStatus.ACTIVE) =
        MenuItem(id, id, MenuCategory.BAKERY, unit, price, "", status, null, 0, 0)

    @Test fun `reference total uses menu prices times quantities`() {
        val result = estimateBoxValue(listOf(item("bread", 35000), item("drink", 25000)), mapOf("bread" to "2", "drink" to "1"))
        assertEquals(95000L, result.total)
        assertEquals(70000L, result.lineValues["bread"])
        assertTrue(result.canApply)
    }
    @Test fun `kilograms accept decimal comma and round each line to whole dong`() {
        val result = estimateBoxValue(listOf(item("fruit", 35001, MenuUnit.KILOGRAM)), mapOf("fruit" to "0,5"))
        assertEquals(17501L, result.total)
        assertTrue(result.canApply)
    }
    @Test fun `invalid quantity never becomes an applicable partial total`() {
        val items = listOf(item("bread", 35000), item("fruit", 20000, MenuUnit.KILOGRAM))
        for (raw in listOf("-1", "1.5", "abc", "10000", "1e3")) {
            val result = estimateBoxValue(items, mapOf("bread" to raw, "fruit" to "1"))
            assertTrue(raw, "bread" in result.invalidIds)
            assertFalse(raw, result.canApply)
        }
        assertFalse(estimateBoxValue(items, mapOf("fruit" to "0.0001")).canApply)
    }
    @Test fun `inactive and missing menu items cannot contribute a value`() {
        val result = estimateBoxValue(listOf(item("old", 35000, status = MenuStatus.INACTIVE)), mapOf("old" to "1", "gone" to "1"))
        assertEquals(setOf("old", "gone"), result.invalidIds)
        assertEquals(0L, result.total)
        assertFalse(result.canApply)
    }
    @Test fun `zero and blank quantities clear selection and empty cannot apply`() {
        val result = estimateBoxValue(listOf(item("a", 35000), item("b", 25000)), mapOf("a" to "0", "b" to ""))
        assertTrue(result.invalidIds.isEmpty())
        assertTrue(result.lineValues.isEmpty())
        assertFalse(result.canApply)
    }
    @Test fun `zero kilograms with decimal places clears the line`() {
        val result = estimateBoxValue(listOf(item("fruit", 35000, MenuUnit.KILOGRAM)), mapOf("fruit" to "0,000"))
        assertTrue(result.invalidIds.isEmpty())
        assertTrue(result.lineValues.isEmpty())
        assertFalse(result.canApply)
    }
    @Test fun `total respects the existing minimum value field limit without overflow`() {
        val items = listOf(item("a", 999999999), item("b", 999999999))
        assertTrue(estimateBoxValue(items, mapOf("a" to "1")).canApply)
        val result = estimateBoxValue(items, mapOf("a" to "9999", "b" to "9999"))
        assertEquals(19997999980002L, result.total)
        assertFalse(result.canApply)
        assertFalse(estimateBoxValue(listOf(item("one", 1)), mapOf("one" to "1")).canApply)
    }
}
