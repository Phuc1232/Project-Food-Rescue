package vn.foodrescue.app.ui.customer

import android.content.Context

/** Release never opens the preview customer flow or records simulated payments. */
internal fun createCustomerStorage(context: Context): CustomerStorage = error("Customer API is not connected")
