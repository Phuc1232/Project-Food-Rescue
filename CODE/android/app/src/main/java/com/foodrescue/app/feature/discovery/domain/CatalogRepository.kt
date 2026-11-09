package com.foodrescue.app.feature.discovery.domain

import com.foodrescue.app.feature.surprisebox.domain.CatalogBox

internal interface CatalogRepository {
    fun getBoxes(): List<CatalogBox>
}
