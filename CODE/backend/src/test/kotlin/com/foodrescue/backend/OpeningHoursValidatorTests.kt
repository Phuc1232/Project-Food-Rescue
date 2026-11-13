// AC-03: OpeningHoursValidatorTests responsibility.
package com.foodrescue.backend

import com.foodrescue.backend.store.dto.OpeningHourRequest
import com.foodrescue.backend.store.service.OpeningHoursValidator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalTime

class OpeningHoursValidatorTests {
    private val validator = OpeningHoursValidator()
    private fun h(day: Int?, opens: String?, closes: String?) = OpeningHourRequest(day, opens, closes)
    private fun errors(vararg hours: OpeningHourRequest) = validator.validate(hours.toList()).second.map { it.field to it.message }

    @Test fun `valid week with split and overnight shifts is normalized`() {
        val (shifts, errs) = validator.validate(listOf(h(1, "17:00", "21:00"), h(1, "06:00", "10:00"), h(5, "18:00", "01:00"), h(7, "20:00", "00:00")))
        assertTrue(errs.isEmpty(), errs.toString())
        assertEquals(listOf(1 to 1, 1 to 2, 5 to 1, 7 to 1), shifts.map { it.dayOfWeek to it.shiftNumber })
        assertEquals(LocalTime.of(6, 0), shifts[0].opensAt)
        assertFalse(shifts[0].closesNextDay); assertTrue(shifts[2].closesNextDay); assertTrue(shifts[3].closesNextDay)
    }

    @Test fun `overlaps are rejected including overnight spill and Sunday into Monday`() {
        assertEquals(listOf("openingHours[1]" to "Shift overlaps openingHours[0]"), errors(h(1, "08:00", "12:00"), h(1, "11:00", "14:00")))
        assertEquals(listOf("openingHours[1]" to "Shift overlaps openingHours[0]"), errors(h(2, "22:00", "03:00"), h(3, "02:00", "08:00")))
        assertEquals(listOf("openingHours[1]" to "Shift overlaps openingHours[0]"), errors(h(7, "22:00", "02:00"), h(1, "01:00", "09:00")))
        assertTrue(errors(h(1, "08:00", "12:00"), h(1, "12:00", "14:00")).isEmpty(), "touching shifts are allowed")
        assertTrue(errors(h(7, "22:00", "02:00"), h(1, "02:00", "09:00")).isEmpty())
    }

    @Test fun `malformed empty and too many shifts are reported per index`() {
        assertEquals(listOf("openingHours[0]" to "Invalid value"), errors(h(8, "08:00", "12:00")))
        assertEquals(listOf("openingHours[0]" to "Invalid value"), errors(h(1, "24:00", "12:00")))
        assertEquals(listOf("openingHours[0]" to "Invalid value"), errors(h(1, "8:00", "12:00")))
        assertEquals(listOf("openingHours[0]" to "Invalid value"), errors(h(null, "08:00", "12:00")))
        assertEquals(listOf("openingHours[0]" to "Shift must not be empty"), errors(h(1, "08:00", "08:00")))
        val four = errors(h(2, "06:00", "07:00"), h(2, "08:00", "09:00"), h(2, "10:00", "11:00"), h(2, "12:00", "13:00"))
        assertEquals(4, four.size); assertTrue(four.all { it.second == "At most 3 shifts per day" })
    }
}
