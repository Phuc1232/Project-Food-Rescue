// AC-01,02,10 (T4.1): FoodCategoryRepository responsibility.
package com.foodrescue.backend.catalog.repository

import com.foodrescue.backend.catalog.entity.FoodCategory
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FoodCategoryRepository : JpaRepository<FoodCategory, UUID> {
    fun findByCode(code: String): FoodCategory?
    fun findAllByActiveTrueOrderByCode(): List<FoodCategory>
}
