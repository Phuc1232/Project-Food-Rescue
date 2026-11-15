package com.foodrescue.app.feature.surprisebox.data

import android.annotation.SuppressLint
import android.content.Context
import com.foodrescue.app.core.datastore.LocalCommerceLock
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.store.domain.PublicStore
import com.foodrescue.app.feature.surprisebox.domain.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Published batches retain stable public IDs; availability derives from the same order ledger. */
internal class LocalSaleCatalogRepository(context: Context, private val stores: () -> List<PublicStore>,
    private val stock: BoxStockSource, private val clock: () -> Long = System::currentTimeMillis) : CatalogRepository {
    private val context = context.applicationContext
    private val directory = this.context.getSharedPreferences("owner_store_directory.v1", Context.MODE_PRIVATE)
    private val ids = this.context.getSharedPreferences("sale_catalog_ids.v1", Context.MODE_PRIVATE)
    override fun getBoxes(): List<CatalogBox> = synchronized(LocalCommerceLock) {
        val now = clock()
        val profiles = stores().associateBy { it.id }
        directory.all.flatMap { (key, id) ->
            val store = (id as? Int)?.let(profiles::get) ?: return@flatMap emptyList()
            if (!key.startsWith("store.")) return@flatMap emptyList()
            val raw = context.getSharedPreferences("owner_boxes_" + key.removePrefix("store."), Context.MODE_PRIVATE)
                .getString("catalog.v1", null) ?: return@flatMap emptyList()
            OwnerBoxJson.decode(raw).batches.filter {
                it.control == BatchControl.PUBLISHED && now >= it.salesStart && now < it.salesEnd
            }.map { batch ->
                val counts = stock.stock(store.id, batch.id, now)
                val time = SimpleDateFormat("HH:mm", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }
                CatalogBox(publicId(store.id, batch.id), store.name, batch.template.category, batch.template.name,
                    batch.template.price.toInt(), batch.template.minimumValue.toInt(), (batch.quantity - counts.held - counts.sold).coerceAtLeast(0),
                    time.format(Date(batch.pickupStart)) + " – " + time.format(Date(batch.pickupEnd)),
                    batch.template.description, batch.template.allergens, store.area, 0,
                    time.format(Date(batch.pickupStart)).take(2).toInt(), store.name.take(2).uppercase(Locale.ROOT), store.location, store.id,
                    time.format(Date(batch.pickupEnd)).take(2).toInt(), batch.id, batch.salesStart, batch.salesEnd, batch.pickupStart, batch.pickupEnd)
            }
        }
    }
    @SuppressLint("UseKtx")
    private fun publicId(store: Int, batch: String): Int {
        val key = "$store.$batch"
        if (ids.contains(key)) return ids.getInt(key, 0)
        val next = ids.getInt("next", -1_000_000)
        check(next > Int.MIN_VALUE)
        check(ids.edit().putInt(key, next).putInt("next", next - 1).commit())
        return next
    }
}
