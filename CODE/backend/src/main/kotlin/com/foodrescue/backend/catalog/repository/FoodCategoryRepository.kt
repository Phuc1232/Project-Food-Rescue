package com.foodrescue.backend.catalog.repository

import com.foodrescue.backend.catalog.entity.FoodCategory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FoodCategoryRepository : JpaRepository<FoodCategory, UUID>
