package com.foodrescue.backend.catalog.repository

import com.foodrescue.backend.catalog.entity.MenuPriceHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MenuPriceHistoryRepository : JpaRepository<MenuPriceHistory, UUID>
