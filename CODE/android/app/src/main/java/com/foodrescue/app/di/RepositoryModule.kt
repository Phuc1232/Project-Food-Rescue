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
import com.foodrescue.app.feature.order.data.LocalOrderHistoryStore
import com.foodrescue.app.feature.payment.data.LocalPaymentRepository
import com.foodrescue.app.feature.complaint.data.LocalComplaintRepository
import com.foodrescue.app.feature.complaint.data.LocalEvidenceStore
import com.foodrescue.app.feature.complaint.presentation.ComplaintViewModel

/** Composition root for the customer frontend's local dependencies. */
object RepositoryModule {
    private val catalogRepository: CatalogRepository = LocalCatalogRepository()

    internal val discoveryViewModelFactory = viewModelFactory {
        initializer { DiscoveryViewModel(createSavedStateHandle(), catalogRepository) }
    }

    internal fun bookingViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            BookingViewModel(createSavedStateHandle(), catalogRepository,
                LocalPaymentRepository(context), LocalOrderHistoryStore(context, email))
        }
    }

    internal fun complaintViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            val evidence = LocalEvidenceStore(context, email)
            ComplaintViewModel(LocalComplaintRepository(context, email, evidence), evidence)
        }
    }

    internal fun locationSource(context: Context): LocationSource =
        AndroidLocationSource(context.applicationContext)
}
