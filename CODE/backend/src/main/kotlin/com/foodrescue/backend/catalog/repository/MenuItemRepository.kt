package com.foodrescue.backend.catalog.repository

import com.foodrescue.backend.catalog.entity.MenuItem
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MenuItemRepository : JpaRepository<MenuItem, UUID>
