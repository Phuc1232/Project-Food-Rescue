package com.foodrescue.app.di

import android.content.Context
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.foodrescue.app.feature.discovery.data.AndroidLocationSource
import com.foodrescue.app.feature.discovery.data.LocalCatalogRepository
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.discovery.domain.LocationSource
import com.foodrescue.app.feature.discovery.presentation.DiscoveryViewModel
import com.foodrescue.app.feature.order.presentation.BookingViewModel
import com.foodrescue.app.feature.payment.data.LocalPaymentRepository

/** Composition root for the customer frontend's local dependencies. */
object RepositoryModule {
    private val catalogRepository: CatalogRepository = LocalCatalogRepository()

    internal val discoveryViewModelFactory = viewModelFactory {
        initializer { DiscoveryViewModel(createSavedStateHandle(), catalogRepository) }
    }

    internal val bookingViewModelFactory = viewModelFactory {
        initializer {
            BookingViewModel(createSavedStateHandle(), catalogRepository,
                LocalPaymentRepository(checkNotNull(this[APPLICATION_KEY])))
        }
    }

    internal fun locationSource(context: Context): LocationSource =
        AndroidLocationSource(context.applicationContext)
}
