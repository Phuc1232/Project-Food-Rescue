// AC-01,03: OpeningHoursValidator responsibility.
package com.foodrescue.backend.store.service

import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.store.dto.OpeningHourRequest
import org.springframework.stereotype.Component
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** A validated shift ready to persist. */
data class OpeningShift(val dayOfWeek: Int, val shiftNumber: Int, val opensAt: LocalTime, val closesAt: LocalTime, val closesNextDay: Boolean)

/**
 * Shifts become half-open minute ranges within the week; ranges may not be empty or overlap, including a Sunday
 * overnight shift running into Monday. At most [MAX_PER_DAY] shifts start on the same day.
 */
@Component
class OpeningHoursValidator {
    fun validate(hours: List<OpeningHourRequest>, field: String = "openingHours"): Pair<List<OpeningShift>, List<FieldViolation>> {
        val errors = mutableListOf<FieldViolation>()
        val parsed = hours.mapIndexedNotNull { i, h ->
            val day = h.dayOfWeek; val opens = time(h.opensAt); val closes = time(h.closesAt)
            when {
                day == null || day !in 1..7 || opens == null || closes == null -> { errors += FieldViolation("$field[$i]", INVALID); null }
                opens == closes -> { errors += FieldViolation("$field[$i]", "Shift must not be empty"); null }
                else -> Parsed(i, day, opens, closes, !closes.isAfter(opens))
            }
        }
        parsed.groupBy { it.day }.filterValues { it.size > MAX_PER_DAY }.values.flatten()
            .forEach { errors += FieldViolation("$field[${it.index}]", "At most $MAX_PER_DAY shifts per day") }
        for ((a, b) in parsed.flatMapIndexed { i, a -> parsed.drop(i + 1).map { a to it } })
            if (overlaps(a, b)) errors += FieldViolation("$field[${b.index}]", "Shift overlaps $field[${a.index}]")
        if (errors.isNotEmpty()) return emptyList<OpeningShift>() to errors.distinct()
        val shifts = parsed.groupBy { it.day }.toSortedMap().flatMap { (_, dayShifts) ->
            dayShifts.sortedBy { it.opens }.mapIndexed { n, s -> OpeningShift(s.day, n + 1, s.opens, s.closes, s.nextDay) }
        }
        return shifts to emptyList()
    }

    private data class Parsed(val index: Int, val day: Int, val opens: LocalTime, val closes: LocalTime, val nextDay: Boolean) {
        val start = (day - 1) * DAY + opens.toSecondOfDay() / 60
        val end = start + (closes.toSecondOfDay() - opens.toSecondOfDay()) / 60 + if (nextDay) DAY else 0
    }

    private fun overlaps(a: Parsed, b: Parsed) =
        listOf(-WEEK, 0, WEEK).any { shift -> a.start < b.end + shift && b.start + shift < a.end }

    private fun time(value: String?): LocalTime? =
        if (value == null || !value.matches(Regex(OpeningHourRequest.TIME))) null
        else try { LocalTime.parse(value) } catch (_: DateTimeParseException) { null }

    private companion object {
        const val DAY = 1440
        const val WEEK = 7 * DAY
        const val MAX_PER_DAY = 3
        const val INVALID = "Invalid value"
    }
}
