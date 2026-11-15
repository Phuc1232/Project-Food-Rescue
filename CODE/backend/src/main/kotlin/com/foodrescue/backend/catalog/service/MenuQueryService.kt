// AC-07,08,10 (T4.1): MenuQueryService responsibility.
package com.foodrescue.backend.catalog.service

import com.foodrescue.backend.catalog.MenuItemStatus
import com.foodrescue.backend.catalog.dto.FoodCategoryView
import com.foodrescue.backend.catalog.dto.MenuItemDetail
import com.foodrescue.backend.catalog.dto.MenuItemView
import com.foodrescue.backend.catalog.repository.FoodCategoryRepository
import com.foodrescue.backend.catalog.repository.MenuItemRepository
import com.foodrescue.backend.common.exception.ApiException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MenuQueryService(
    private val access: MenuAccess,
    private val items: MenuItemRepository,
    private val categories: FoodCategoryRepository,
    private val views: MenuViews
) {
    @Transactional(readOnly = true)
    fun categories(): List<FoodCategoryView> = categories.findAllByActiveTrueOrderByCode().map { FoodCategoryView(it.code, it.name) }

    @Transactional(readOnly = true)
    fun list(accountId: UUID, storeId: UUID, status: MenuItemStatus?, categoryCode: String?): List<MenuItemView> {
        access.requireRead(accountId, storeId)
        return items.findAllByStoreIdOrderByName(storeId)
            .filter { (status == null || it.status == status) && (categoryCode == null || it.category.code == categoryCode) }.map(views::item)
    }

    @Transactional(readOnly = true)
    fun detail(accountId: UUID, storeId: UUID, itemId: UUID): MenuItemDetail {
        access.requireRead(accountId, storeId)
        return views.detail(items.findById(itemId).orElse(null)?.takeIf { it.store.id == storeId }
            ?: throw ApiException(404, "NOT_FOUND", "Resource not found"))
    }
}
