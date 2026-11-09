package com.foodrescue.app.core.util

import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

fun money(value: Int): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ"
internal fun money(value: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value) + " đ"
fun distance(meters: Int): String = if (meters < 0) "Chưa có khoảng cách" else String.format(Locale.forLanguageTag("vi-VN"), "%.1f km", meters / 1000.0)
internal fun String.searchKey() = Normalizer.normalize(lowercase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "")
