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

/** Replaceable frontend adapter; no payment-provider request is sent. */
internal class LocalRefundProcessor(context: Context) : RefundProcessor {
    private val repository = LocalRefundRepository(context)
    override fun check(key: String, paymentReference: String, amount: Long): RefundResult {
        require(key.isNotBlank() && paymentReference.isNotBlank() && amount > 0)
        return repository.check(OrderRefund(amount, RefundStatus.PROCESSING, 0, 0))
    }
}
