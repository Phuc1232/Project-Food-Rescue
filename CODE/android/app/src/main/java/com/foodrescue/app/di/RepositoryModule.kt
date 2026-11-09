package com.foodrescue.app.di

import android.content.Context
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.foodrescue.app.feature.discovery.data.AndroidLocationSource
import com.foodrescue.app.feature.discovery.data.LocalCatalogRepository
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.feature.discovery.domain.LocationSource
import com.foodrescue.app.feature.discovery.presentation.DiscoveryViewModel

/** Composition root for the customer frontend's local dependencies. */
object RepositoryModule {
    private val catalogRepository: CatalogRepository = LocalCatalogRepository()

    internal val discoveryViewModelFactory = viewModelFactory {
        initializer { DiscoveryViewModel(createSavedStateHandle(), catalogRepository) }
    }

    internal fun locationSource(context: Context): LocationSource =
        AndroidLocationSource(context.applicationContext)
}
