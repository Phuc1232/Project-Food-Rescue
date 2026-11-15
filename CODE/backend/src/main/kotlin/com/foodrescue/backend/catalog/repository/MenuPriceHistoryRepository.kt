// AC-01,03,05,08 (T4.1): MenuPriceHistoryRepository responsibility.
package com.foodrescue.backend.catalog.repository

import com.foodrescue.backend.catalog.entity.MenuPriceHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Insert-only (trigger V3 rejects UPDATE/DELETE). */
interface MenuPriceHistoryRepository : JpaRepository<MenuPriceHistory, UUID> {
    fun findAllByMenuItemIdOrderByChangedAtDescCreatedAtDesc(menuItemId: UUID): List<MenuPriceHistory>
}
