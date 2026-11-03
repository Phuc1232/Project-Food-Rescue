package vn.foodrescue.app.ui.customer

import android.content.Context
import com.google.gson.Gson

/** Local fixture data, separate from any future authenticated repository. No money is collected. */
internal fun createCustomerStorage(context: Context): CustomerStorage = object : CustomerStorage {
    private val prefs = context.getSharedPreferences("customer_preview_v1", Context.MODE_PRIVATE)
    private val gson = Gson()
    override fun load(): CustomerSnapshot = runCatching {
        val json = prefs.getString("snapshot", null) ?: return CustomerSnapshot()
        val loaded = gson.fromJson(json, CustomerSnapshot::class.java)
        require(loaded.version == 1 && loaded.orders.size <= 200)
        require(loaded.profile.name.isNotBlank())
        loaded.orders.forEach {
            require(it.quantity in 1..100 && it.total > 0 && it.id.isNotBlank())
            require(it.refunded in 0..it.refundDue && it.refundDue <= it.paidInApp)
            require(it.policy.depositPercent in 1..100)
        }
        loaded
    }.getOrElse { CustomerSnapshot() }
    override fun save(snapshot: CustomerSnapshot) { prefs.edit().putString("snapshot", gson.toJson(snapshot)).apply() }
}
