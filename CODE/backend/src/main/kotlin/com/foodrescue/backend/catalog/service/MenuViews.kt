// AC-01,03,08,09 (T4.1): MenuViews responsibility.
package com.foodrescue.backend.catalog.service

import com.foodrescue.backend.catalog.dto.*
import com.foodrescue.backend.catalog.entity.MenuItem
import com.foodrescue.backend.catalog.entity.PriceReferenceDocument
import com.foodrescue.backend.catalog.repository.MenuPriceHistoryRepository
import com.foodrescue.backend.catalog.repository.PriceReferenceDocumentRepository
import org.springframework.stereotype.Component

/** Must run inside a transaction. */
@Component
class MenuViews(private val history: MenuPriceHistoryRepository, private val documents: PriceReferenceDocumentRepository) {
    fun item(m: MenuItem) = MenuItemView(m.id!!, m.name, m.category.code, m.category.name, m.listedPrice.setScale(0), m.ingredients,
        m.safetyWarnings, m.status.name, m.version)

    fun detail(m: MenuItem) = MenuItemDetail(item(m),
        history.findAllByMenuItemIdOrderByChangedAtDescCreatedAtDesc(m.id!!).map {
            PriceHistoryView(it.previousPrice.setScale(0), it.newPrice.setScale(0), it.changedAt, it.changedBy.id!!, it.reason) },
        documents.findForItem(m.store.id!!, m.id!!).map(::document))

    fun document(d: PriceReferenceDocument) = PriceDocumentView(d.id!!, d.menuItem?.id, d.file.id!!, d.file.originalName, d.description,
        d.appliesFrom, d.uploadedAt, d.replacedBy?.id)
}
