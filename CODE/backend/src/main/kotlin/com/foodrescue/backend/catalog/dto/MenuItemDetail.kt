// AC-01,03,06,08 (T4.1): MenuItemDetail responsibility.
package com.foodrescue.backend.catalog.dto

/** Item with its price versions (newest first) and the reference documents for it or the whole menu. */
data class MenuItemDetail(val item: MenuItemView, val priceHistory: List<PriceHistoryView>, val documents: List<PriceDocumentView>)
