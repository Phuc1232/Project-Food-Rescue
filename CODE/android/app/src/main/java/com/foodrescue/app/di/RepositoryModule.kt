package com.foodrescue.app.di

import com.foodrescue.app.feature.store.presentation.StoreApplicationsViewModel
import com.foodrescue.app.feature.store.data.LocalStoreApplicationRepository
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
import com.foodrescue.app.feature.staff.presentation.StaffPackingViewModel
import com.foodrescue.app.feature.staff.data.LocalStaffPackingRepository
import com.foodrescue.app.feature.staff.data.LocalStaffHandoverRepository
import com.foodrescue.app.feature.staff.presentation.StaffHandoverViewModel
import com.foodrescue.app.feature.staff.presentation.StaffBoxConditionViewModel
import com.foodrescue.app.feature.staff.presentation.StaffNoShowViewModel
import com.foodrescue.app.feature.staff.data.LocalStaffNoShowRepository
import com.foodrescue.app.feature.staff.data.LocalStaffBoxConditionRepository
import com.foodrescue.app.feature.complaint.data.LocalComplaintHolds
import com.foodrescue.app.feature.finance.data.LocalRevenueRepository
import com.foodrescue.app.feature.finance.data.LocalSettlementStore
import com.foodrescue.app.feature.finance.presentation.RevenueViewModel

/** Composition root for frontend local dependencies. */
object RepositoryModule {
    internal fun adminFinanceViewModelFactory(email: String) = viewModelFactory {
        initializer { com.foodrescue.app.feature.finance.presentation.AdminFinanceViewModel(createSavedStateHandle(),
            com.foodrescue.app.feature.finance.data.LocalAdminSettlementRepository(checkNotNull(this[APPLICATION_KEY]), email)) }
    }
    internal fun financialPolicyViewModelFactory(email: String) = viewModelFactory {
        initializer { com.foodrescue.app.feature.finance.presentation.FinancialPolicyViewModel(
            com.foodrescue.app.feature.finance.data.LocalFinancialPolicyRepository(checkNotNull(this[APPLICATION_KEY]), email)) }
    }
    internal val accountAccessViewModelFactory = viewModelFactory {
        initializer { com.foodrescue.app.feature.account.presentation.AccountAccessViewModel(
            accountSessionRepository(checkNotNull(this[APPLICATION_KEY]))) }
    }
    internal fun accountSessionRepository(context: Context): com.foodrescue.app.feature.account.domain.AccountSessionRepository =
        com.foodrescue.app.feature.account.data.LocalAccountSessionRepository(context)

    internal fun adminUsersViewModelFactory(email: String) = viewModelFactory {
        initializer {
            com.foodrescue.app.feature.account.presentation.AdminUsersViewModel(createSavedStateHandle(),
                com.foodrescue.app.feature.account.data.LocalAccountManagementRepository(checkNotNull(this[APPLICATION_KEY]), email))
        }
    }
    internal fun storeSupervisionViewModelFactory(email: String) = viewModelFactory {
        initializer {
            com.foodrescue.app.feature.store.presentation.StoreSupervisionViewModel(createSavedStateHandle(),
                com.foodrescue.app.feature.store.data.LocalStoreSupervisionRepository(checkNotNull(this[APPLICATION_KEY]), email))
        }
    }
    internal fun adminRefundsViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            com.foodrescue.app.feature.refund.presentation.AdminRefundsViewModel(createSavedStateHandle(),
                com.foodrescue.app.feature.refund.data.LocalAdminRefundRepository(context, email,
                    com.foodrescue.app.feature.refund.data.LocalRefundProcessor(context)))
        }
    }
    internal fun adminComplaintsViewModelFactory(email: String) = viewModelFactory {
        initializer {
            com.foodrescue.app.feature.complaint.presentation.AdminComplaintsViewModel(createSavedStateHandle(),
                com.foodrescue.app.feature.complaint.data.LocalComplaintReviewRepository(checkNotNull(this[APPLICATION_KEY]), email))
        }
    }
    private const val AUTO_APPROVE_STORE_APPLICATIONS = false
    // Owner revenue policy confirmed by the product owner: 20%, rounded per order.
    private const val OWNER_COMMISSION_BPS = 2000
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
                    LocalOrderHistoryStore(context, email), catalog), LocalRefundRepository(context),
                policies = com.foodrescue.app.feature.finance.data.LocalFinancialPolicyStore(context))
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
    internal fun revenueViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            val storeId = LocalPublicStoreRepository(context).register(LocalOrderLedger.accountKey(email))
            RevenueViewModel(createSavedStateHandle(), LocalRevenueRepository(storeId,
                LocalOwnerStoreRepository(context, email), LocalOrderLedger(context), LocalSettlementStore(context),
                LocalComplaintHolds(context), OWNER_COMMISSION_BPS,
                policy = com.foodrescue.app.feature.finance.data.LocalFinancialPolicyStore(context)))
        }
    }
    internal fun storeApplicationsViewModelFactory(email: String) = viewModelFactory {
        initializer {
            StoreApplicationsViewModel(createSavedStateHandle(),
                LocalStoreApplicationRepository(checkNotNull(this[APPLICATION_KEY]), email))
        }
    }
    internal fun staffAccessRepository(context: Context): StaffAccessRepository = LocalEmployeeDirectory(context)
    internal fun staffAccessViewModelFactory(email: String) = viewModelFactory {
        initializer { StaffAccessViewModel(email, staffAccessRepository(checkNotNull(this[APPLICATION_KEY]))) }
    }
    internal fun staffPackingViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            StaffPackingViewModel(createSavedStateHandle(), LocalStaffPackingRepository(context, email, LocalEmployeeDirectory(context)))
        }
    }
    internal fun staffHandoverViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            StaffHandoverViewModel(createSavedStateHandle(), LocalStaffHandoverRepository(context, email, LocalEmployeeDirectory(context)))
        }
    }
    internal fun staffBoxConditionViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            StaffBoxConditionViewModel(createSavedStateHandle(), LocalStaffBoxConditionRepository(context, email, LocalEmployeeDirectory(context)))
        }
    }
    internal fun staffNoShowViewModelFactory(email: String) = viewModelFactory {
        initializer {
            val context = checkNotNull(this[APPLICATION_KEY])
            StaffNoShowViewModel(createSavedStateHandle(), LocalStaffNoShowRepository(context, email, LocalEmployeeDirectory(context)))
        }
    }

}
