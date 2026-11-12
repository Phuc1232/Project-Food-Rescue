package com.foodrescue.app.feature.refund.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.foodrescue.app.feature.refund.domain.*

/** Frontend result adapter. No bank request or money movement is performed. */
internal class LocalRefundRepository(context: Context) : RefundRepository {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    override fun check(refund: OrderRefund): RefundResult {
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        return if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
            RefundResult.COMPLETED
        } else RefundResult.UNAVAILABLE
    }
}
