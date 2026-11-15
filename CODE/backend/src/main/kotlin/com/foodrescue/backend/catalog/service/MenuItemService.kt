// AC-01,02,03,04,06 (T4.1): MenuItemService responsibility.
package com.foodrescue.backend.catalog.service

import com.foodrescue.backend.account.repository.AccountRepository
import com.foodrescue.backend.catalog.MenuItemStatus
import com.foodrescue.backend.catalog.dto.*
import com.foodrescue.backend.catalog.entity.FoodCategory
import com.foodrescue.backend.catalog.entity.MenuItem
import com.foodrescue.backend.catalog.entity.MenuPriceHistory
import com.foodrescue.backend.catalog.repository.FoodCategoryRepository
import com.foodrescue.backend.catalog.repository.MenuItemRepository
import com.foodrescue.backend.catalog.repository.MenuPriceHistoryRepository
import com.foodrescue.backend.common.exception.ApiException
import com.foodrescue.backend.common.exception.authenticationFailed
import com.foodrescue.backend.common.response.FieldViolation
import com.foodrescue.backend.store.repository.StoreRepository
import com.foodrescue.backend.store.service.StoreHistoryRecorder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/** Owner writes of UC-50..52. Price changes and their history are written in one transaction (BR-07, UC-51). */
@Service
class MenuItemService(
    private val access: MenuAccess,
    private val stores: StoreRepository,
    private val items: MenuItemRepository,
    private val categories: FoodCategoryRepository,
    private val prices: MenuPriceHistoryRepository,
    private val accounts: AccountRepository,
    private val priceValidator: PriceValidator,
    private val history: StoreHistoryRecorder,
    private val views: MenuViews,
    private val clock: Clock
) {
    @Transactional
    fun create(ownerId: UUID, storeId: UUID, r: MenuItemCreateRequest): MenuItemDetail {
        access.requireWrite(ownerId, storeId)
        val errors = mutableListOf<FieldViolation>()
        val category = category(r.categoryCode!!, errors)
        priceValidator.check(r.listedPrice!!, "listedPrice")?.let(errors::add)
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)
        val store = stores.findById(storeId).orElse(null) ?: notFound()
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        val price = priceValidator.normalize(r.listedPrice)
        val item = items.saveAndFlush(MenuItem(store, category!!, r.name!!.trim(), price, r.ingredients!!.trim(), r.safetyWarnings!!.trim(),
            if (r.active == false) MenuItemStatus.DISCONTINUED else MenuItemStatus.ACTIVE))
        prices.save(MenuPriceHistory(item, price, price, owner, clock.instant(), INITIAL))
        history.record(owner, store, ENTITY, item.id!!, "MENU_ITEM_CREATED", null,
            mapOf("name" to item.name, "listedPrice" to price, "status" to item.status.name))
        return views.detail(item)
    }

    /** Row lock + version check: concurrent edits of the same version give one 200 and one 409 (UC-51 E2). */
    @Transactional
    fun update(ownerId: UUID, storeId: UUID, itemId: UUID, r: MenuItemUpdateRequest): MenuItemDetail {
        access.requireWrite(ownerId, storeId)
        val item = locked(storeId, itemId, r.version!!)
        val errors = mutableListOf<FieldViolation>()
        fun text(value: String?, field: String) = value?.trim()?.also { if (it.isEmpty()) errors += FieldViolation(field, "Must not be blank") }
        val name = text(r.name, "name"); val ingredients = text(r.ingredients, "ingredients"); val warnings = text(r.safetyWarnings, "safetyWarnings")
        val category = r.categoryCode?.let { category(it, errors) }
        val reason = r.priceChangeReason?.trim()?.takeIf { it.isNotEmpty() }
        val priceError = r.listedPrice?.let { priceValidator.check(it, "listedPrice") }
        if (priceError != null) errors += priceError
        val newPrice = r.listedPrice?.takeIf { priceError == null }?.let(priceValidator::normalize)?.takeIf { it.compareTo(item.listedPrice) != 0 }
        if (newPrice != null && reason == null) errors += FieldViolation("priceChangeReason", "Required when the price changes")
        if (errors.isNotEmpty()) throw ApiException(400, "VALIDATION_FAILED", "Invalid request", errors)

        val before = linkedMapOf<String, Any?>(); val after = linkedMapOf<String, Any?>()
        fun change(field: String, old: Any?, new: Any?, apply: () -> Unit) { if (new != null && new != old) { before[field] = old; after[field] = new; apply() } }
        change("name", item.name, name) { item.name = name!! }
        change("ingredients", item.ingredients, ingredients) { item.ingredients = ingredients!! }
        change("safetyWarnings", item.safetyWarnings, warnings) { item.safetyWarnings = warnings!! }
        change("categoryCode", item.category.code, category?.code) { item.category = category!! }
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        if (newPrice != null) {
            before["listedPrice"] = item.listedPrice.setScale(0); after["listedPrice"] = newPrice; after["priceChangeReason"] = reason
            prices.save(MenuPriceHistory(item, item.listedPrice, newPrice, owner, clock.instant(), reason!!))
            item.listedPrice = newPrice
        }
        if (after.isEmpty()) return views.detail(item)
        items.saveAndFlush(item)
        history.record(owner, item.store, ENTITY, item.id!!, "MENU_ITEM_UPDATED", before, after)
        return views.detail(item)
    }

    @Transactional
    fun changeStatus(ownerId: UUID, storeId: UUID, itemId: UUID, r: MenuItemStatusRequest): MenuItemDetail {
        access.requireWrite(ownerId, storeId)
        val item = locked(storeId, itemId, r.version!!)
        val target = r.status!!
        if (item.status == target) throw ApiException(409, "CONFLICT", "Menu item is already $target")
        // UC-52 A1/E1: reopening requires complete data and an active category.
        if (target == MenuItemStatus.ACTIVE && (!item.category.active || item.name.isBlank() || item.ingredients.isBlank() || item.safetyWarnings.isBlank()))
            throw ApiException(400, "VALIDATION_FAILED", "Invalid request", listOf(FieldViolation("status", "Item data is incomplete or its category is inactive")))
        val owner = accounts.findById(ownerId).orElse(null) ?: authenticationFailed()
        val before = item.status.name
        item.status = target
        items.saveAndFlush(item)
        history.record(owner, item.store, ENTITY, item.id!!, "MENU_ITEM_STATUS_CHANGED", mapOf("status" to before),
            mapOf("status" to target.name, "reason" to r.reason!!.trim()))
        return views.detail(item)
    }

    private fun locked(storeId: UUID, itemId: UUID, version: Long): MenuItem {
        val item = items.findForUpdate(itemId)?.takeIf { it.store.id == storeId } ?: notFound()
        if (item.version != version) throw ApiException(409, "CONFLICT", "Menu item has changed; reload and try again")
        return item
    }

    private fun category(code: String, errors: MutableList<FieldViolation>): FoodCategory? =
        categories.findByCode(code.trim())?.takeIf { it.active } ?: run { errors += FieldViolation("categoryCode", "Unknown category"); null }

    private fun notFound(): Nothing = throw ApiException(404, "NOT_FOUND", "Resource not found")

    companion object {
        const val ENTITY = "MENU_ITEM"
        const val INITIAL = "Initial price"
    }
}
