package com.foodrescue.app.core.util

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun percentageRate(basisPoints: Int): String = BigDecimal(basisPoints).movePointLeft(2)
    .stripTrailingZeros().toPlainString().replace('.', ',') + "%"

internal fun vietnamTimestamp(millis: Long): String =
    SimpleDateFormat("HH:mm · dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }.format(Date(millis))
