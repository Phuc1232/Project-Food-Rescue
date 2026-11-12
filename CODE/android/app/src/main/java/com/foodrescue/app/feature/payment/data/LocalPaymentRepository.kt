package com.foodrescue.app.feature.payment.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.foodrescue.app.feature.payment.domain.PaymentRepository
import com.foodrescue.app.feature.payment.domain.PaymentResult
import com.foodrescue.app.feature.payment.domain.QrPayment

/** Local frontend responses only: no bank request, payment URL, signing key or transfer. */
internal class LocalPaymentRepository(context: Context) : PaymentRepository {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun check(payment: QrPayment): PaymentResult {
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        return if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true) {
            PaymentResult.CONFIRMED
        } else PaymentResult.UNAVAILABLE
    }
}
