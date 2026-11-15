package com.foodrescue.app.feature.surprisebox.domain

internal data class BoxStock(val held: Int = 0, val sold: Int = 0)
internal fun interface BoxStockSource { fun stock(storeId: Int, batchId: String, now: Long): BoxStock }
