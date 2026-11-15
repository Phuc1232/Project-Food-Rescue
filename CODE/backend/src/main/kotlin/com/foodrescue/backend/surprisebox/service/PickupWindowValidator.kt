// AC-01,02 (T4.3): PickupWindowValidator responsibility.
package com.foodrescue.backend.surprisebox.service

import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.store.entity.Store
import com.foodrescue.backend.store.repository.StoreOpeningHourRepository
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * BR-T43-02/03: orderDeadline <= pickupStartsAt < pickupEndsAt, window in the future, at most 24h, and fully inside one opening
 * shift in the store timezone (shifts of the start day and overnight shifts of the previous day are considered).
 */
@Component
class PickupWindowValidator(private val hours: StoreOpeningHourRepository, private val clock: Clock) {
    fun check(store: Store, deadline: Instant, start: Instant, end: Instant, requireFutureDeadline: Boolean): List<FieldViolation> {
        val now = clock.instant()
        return when {
            !start.isBefore(end) -> listOf(FieldViolation("pickupEndsAt", "Must be after pickupStartsAt"))
            deadline.isAfter(start) -> listOf(FieldViolation("orderDeadline", "Must not be after pickupStartsAt"))
            !end.isAfter(now) -> listOf(FieldViolation("pickupEndsAt", "Must be in the future"))
            requireFutureDeadline && !deadline.isAfter(now) -> listOf(FieldViolation("orderDeadline", "Must be in the future"))
            Duration.between(start, end) > MAX_WINDOW -> listOf(FieldViolation("pickupEndsAt", "Pickup window must be at most 24 hours"))
            !insideOpeningHours(store, start, end) -> listOf(FieldViolation("pickupStartsAt", "Pickup window is outside the store opening hours"))
            else -> emptyList()
        }
    }

    fun saleDate(store: Store, start: Instant): LocalDate = start.atZone(ZoneId.of(store.timezone)).toLocalDate()

    private fun insideOpeningHours(store: Store, start: Instant, end: Instant): Boolean {
        val zone = ZoneId.of(store.timezone)
        val day = start.atZone(zone).toLocalDate()
        val shifts = hours.findAllByStoreIdOrderByDayOfWeekAscShiftNumberAsc(store.id!!)
        return listOf(day.minusDays(1), day).any { d ->
            shifts.filter { it.dayOfWeek == d.dayOfWeek.value }.any { s ->
                val opens = d.atTime(s.opensAt).atZone(zone).toInstant()
                val closes = (if (s.closesNextDay) d.plusDays(1) else d).atTime(s.closesAt).atZone(zone).toInstant()
                !start.isBefore(opens) && !end.isAfter(closes)
            }
        }
    }

    private companion object { val MAX_WINDOW: Duration = Duration.ofHours(24) }
}
