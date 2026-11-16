// AC-10 (T4.1): FoodCategoryController responsibility.
package com.foodrescue.backend.catalog.controller

import com.foodrescue.backend.catalog.dto.FoodCategoryView
import com.foodrescue.backend.catalog.service.MenuQueryService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class FoodCategoryController(private val menu: MenuQueryService) {
    @GetMapping("/api/v1/food-categories")
    fun categories(): List<FoodCategoryView> = menu.categories()
}
