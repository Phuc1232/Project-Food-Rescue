package com.foodrescue.app.di

import android.content.Context
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.foodrescue.app.core.util.AndroidLocationSource
import com.foodrescue.app.feature.discovery.data.LocalCatalogRepository
import com.foodrescue.app.feature.discovery.domain.CatalogRepository
import com.foodrescue.app.core.common.LocationSource
import com.foodrescue.app.feature.discovery.presentation.DiscoveryViewModel
import com.foodrescue.app.feature.order.presentation.BookingViewModel
import com.foodrescue.app.feature.order.data.LocalOrderHistoryStore
import com.foodrescue.app.feature.order.data.LocalOrderLedger
import com.foodrescue.app.feature.order.data.SharedOrderHistoryStore
import com.foodrescue.app.feature.order.data.LocalOwnerOrderRepository
import com.foodrescue.app.feature.order.presentation.OwnerOrdersViewModel
import com.foodrescue.app.feature.surprisebox.data.LocalSaleCatalogRepository
import com.foodrescue.app.feature.payment.data.LocalPaymentRepository
import com.foodrescue.app.feature.refund.data.LocalRefundRepository
import com.foodrescue.app.feature.complaint.data.LocalComplaintRepository
import com.foodrescue.app.feature.complaint.data.LocalEvidenceStore
import com.foodrescue.app.feature.complaint.presentation.ComplaintViewModel
import com.foodrescue.app.feature.store.data.LocalOwnerStoreRepository
import com.foodrescue.app.feature.store.data.LocalPublicStoreRepository
import com.foodrescue.app.feature.store.data.LocalPayoutBanks
import com.foodrescue.app.feature.store.presentation.StoreManagementViewModel
import com.foodrescue.app.feature.menu.data.LocalMenuAssetStore
import com.foodrescue.app.feature.menu.data.LocalMenuRepository
import com.foodrescue.app.feature.menu.presentation.MenuViewModel
import com.foodrescue.app.feature.surprisebox.data.LocalOwnerBoxRepository
import com.foodrescue.app.feature.surprisebox.presentation.OwnerBoxesViewModel
import com.foodrescue.app.feature.staff.data.LocalEmployeeDirectory
import com.foodrescue.app.feature.staff.data.LocalEmployeeRepository
import com.foodrescue.app.feature.staff.domain.StaffAccessRepository
import com.foodrescue.app.feature.staff.presentation.EmployeeViewModel
import com.foodrescue.app.feature.staff.presentation.StaffAccessViewModel

/** Composition root for frontend local dependencies. */
object RepositoryModule {
    // Temporary frontend behavior requested by the user. Set false to restore application review.
    private const val AUTO_APPROVE_STORE_APPLICATIONS = true
    private fun catalogRepository(context: Context): CatalogRepository {
        val directory = LocalPublicStoreRepository(context)
        val published = LocalSaleCatalogRepository(context, directory::readStores, LocalOrderLedger(context))
        val fixtures = LocalCatalogRepository()
        return object : CatalogRepository {
            override fun getBoxes() = fixtures.getBoxes() + published.getBoxes()
        }
    }

    internal val discoveryViewModelFactory = viewModelFactory {
        initializer { DiscoveryViewModel(createSavedStateHandle(), catalogRepository(checkNotNull(this[APPLICATION_KEY])),
            LocalPublicStoreRepository(checkNotNull(this[APPLICATION_KEY]))) }
    }

    internal fun bookingViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            val catalog = catalogRepository(context)
            BookingViewModel(createSavedStateHandle(), catalog,
                LocalPaymentRepository(context), SharedOrderHistoryStore(email, LocalOrderLedger(context),
                    LocalOrderHistoryStore(context, email), catalog), LocalRefundRepository(context))
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

    internal fun isStoreOwner(context: Context, email: String): Boolean =
        LocalOwnerStoreRepository(context, email).hasApplication()

    internal fun storeViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            StoreManagementViewModel(createSavedStateHandle(), LocalOwnerStoreRepository(context, email,
                autoApproveSubmittedApplications = AUTO_APPROVE_STORE_APPLICATIONS), LocalPayoutBanks.all)
        }
    }

    internal fun menuViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            val assets = LocalMenuAssetStore(context, email)
            MenuViewModel(createSavedStateHandle(),
                LocalMenuRepository(context, email, LocalOwnerStoreRepository(context, email), assets), assets)
        }
    }

    internal fun ownerBoxesViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            val owner = LocalOwnerStoreRepository(context, email)
            OwnerBoxesViewModel(createSavedStateHandle(), LocalOwnerBoxRepository(context, email, owner, LocalOrderLedger(context),
                LocalPublicStoreRepository(context).register(LocalOrderLedger.accountKey(email))),
                LocalMenuRepository(context, email, owner, LocalMenuAssetStore(context, email)))
        }
    }
    internal fun ownerOrdersViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            val storeId = LocalPublicStoreRepository(context).register(LocalOrderLedger.accountKey(email))
            OwnerOrdersViewModel(createSavedStateHandle(), LocalOwnerOrderRepository(LocalOrderLedger(context),
                storeId, LocalOwnerStoreRepository(context, email)))
        }
    }

    internal fun employeesViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            val storeId = LocalPublicStoreRepository(context).register(LocalOrderLedger.accountKey(email))
            EmployeeViewModel(createSavedStateHandle(), LocalEmployeeRepository(email, storeId,
                LocalOwnerStoreRepository(context, email), LocalEmployeeDirectory(context)))
        }
    }
    internal fun staffAccessRepository(context: Context): StaffAccessRepository = LocalEmployeeDirectory(context)
    internal fun staffAccessViewModelFactory(email: String) = viewModelFactory {
        initializer { StaffAccessViewModel(email, staffAccessRepository(checkNotNull(this[APPLICATION_KEY]))) }
    }

}
