package com.foodrescue.app.feature.finance.domain

import java.util.Calendar
import java.util.TimeZone

internal data class RevenueWindow(val start: Long, val endExclusive: Long) {
    init { require(endExclusive > start) }
    operator fun contains(time: Long): Boolean = time >= start && time < endExclusive
}
internal enum class RevenuePeriod { DAY, WEEK, MONTH;
    fun window(now: Long, offset: Int = 0): RevenueWindow {
        require(offset in -1200..0)
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            when (this@RevenuePeriod) {
                DAY -> Unit
                WEEK -> add(Calendar.DAY_OF_MONTH, -((get(Calendar.DAY_OF_WEEK) + 5) % 7))
                MONTH -> set(Calendar.DAY_OF_MONTH, 1)
            }
        }
        val field = if (this == MONTH) Calendar.MONTH else Calendar.DAY_OF_MONTH
        val step = if (this == WEEK) 7 else 1
        calendar.add(field, offset * step)
        val start = calendar.timeInMillis
        calendar.add(field, step)
        return RevenueWindow(start, calendar.timeInMillis)
    }
}
