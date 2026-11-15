package com.foodrescue.app.feature.menu.domain

import com.foodrescue.app.core.util.searchKey
import java.text.Normalizer
import java.util.Locale

private fun String.identity() = Normalizer.normalize(trim().replace(Regex("\\s+"), " "), Normalizer.Form.NFC).lowercase(Locale.ROOT)

internal fun MenuDraft.validationErrors(items: List<MenuItem>): Set<MenuField> = buildSet {
    if (name.trim().length !in 2..100 || name.any(Char::isISOControl)) add(MenuField.NAME)
    if (!price.trim().matches(Regex("[0-9]+")) || price.trim().toLongOrNull() !in 1L..999_999_999L) add(MenuField.PRICE)
    if (description.trim().length > 500) add(MenuField.DESCRIPTION)
    if (items.any { it.id != id && it.name.identity() == name.identity() && it.category == category && it.unit == unit }) add(MenuField.DUPLICATE)
}

internal fun List<MenuItem>.filterMenu(query: String, category: MenuCategory?, status: MenuStatus?, sort: MenuSort): List<MenuItem> {
    val key = query.trim().searchKey()
    val filtered = filter { (category == null || it.category == category) && (status == null || it.status == status) &&
        (key.isBlank() || (it.name + " " + it.description).searchKey().contains(key)) }
    return when (sort) {
        MenuSort.UPDATED -> filtered.sortedWith(compareByDescending<MenuItem> { it.updatedAtMillis }.thenBy { it.id })
        MenuSort.NAME -> filtered.sortedWith(compareBy<MenuItem> { it.name.searchKey() }.thenBy { it.id })
        MenuSort.PRICE_ASC -> filtered.sortedWith(compareBy<MenuItem> { it.price }.thenBy { it.name.searchKey() })
        MenuSort.PRICE_DESC -> filtered.sortedWith(compareByDescending<MenuItem> { it.price }.thenBy { it.name.searchKey() })
    }
}
