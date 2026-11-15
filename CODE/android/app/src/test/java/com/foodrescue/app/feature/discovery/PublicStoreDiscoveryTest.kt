package com.foodrescue.app.feature.discovery

import com.foodrescue.app.core.common.GeoPoint
import com.foodrescue.app.core.common.PickupLocation
import com.foodrescue.app.feature.discovery.domain.*
import com.foodrescue.app.feature.discovery.presentation.DiscoveryUiState
import com.foodrescue.app.feature.store.domain.PublicStore
import com.foodrescue.app.feature.store.domain.PublicStoreRepository
import com.foodrescue.app.feature.discovery.presentation.DiscoveryViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PublicStoreDiscoveryTest {
    private val store = PublicStore(-10000, "Tiệm Bánh An Nhiên", "Tiệm bánh", "", "0901234567", "Bàn Cờ", "07:00", "21:00",
        PickupLocation(10.777, 106.699, "47 Nguyễn Thượng Hiền, Bàn Cờ, TP. Hồ Chí Minh"))
    private val state = DiscoveryUiState(source = emptyList(), publicStores = listOf(store), district = "TP. Hồ Chí Minh", storeResults = true)

    @Test fun publishedStoreAppearsOnceInSearchAndOnMap() {
        val box = com.foodrescue.app.feature.discovery.data.LocalCatalogRepository().getBoxes().first()
            .copy(id = -1000000, storeId = store.id, store = store.name, district = store.area, location = store.location, batchId = "batch")
        val published = state.copy(source = listOf(box))
        assertEquals(1, published.shops.size)
        assertEquals(1L, published.shops.single().count)
        assertEquals(1, published.mapPresentation.boxes.size + published.mapPresentation.publicStores.size)
    }

    @Test fun approvedStoreWithoutOffersIsSearchableAndMappableButNotBookable() {
        val results = state.copy(query = "an nhien", point = GeoPoint(10.777, 106.699))
        assertEquals(listOf(store), results.mapPresentation.publicStores)
        assertEquals(1L, results.presentation.total)
        assertEquals(0L, results.shops.single().count)
        assertEquals(0, results.shops.single().distanceMeters)
        assertTrue(results.boxes.isEmpty())
    }
    @Test fun storeWithoutOffersDoesNotMatchPricePickupOrWrongArea() {
        assertTrue(state.copy(filters = DiscoveryFilters(price = PriceRange(0, 100000))).visiblePublicStores.isEmpty())
        assertTrue(state.copy(filters = DiscoveryFilters(pickup = PickupBand.BEFORE_18)).visiblePublicStores.isEmpty())
        assertTrue(state.copy(district = "Quận 1").visiblePublicStores.isEmpty())
        assertTrue(state.copy(query = "không tìm thấy").visiblePublicStores.isEmpty())
        assertEquals(listOf(store), state.copy(district = "Bàn Cờ").visiblePublicStores)
    }
    @Test fun directoryRefreshPreservesSearchTypedWhileStorageIsLoading() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val repository = object : PublicStoreRepository {
            override suspend fun getStores(): List<PublicStore> { ready.await(); return listOf(store) }
        }
        val catalog = object : CatalogRepository { override fun getBoxes() = emptyList<com.foodrescue.app.feature.surprisebox.domain.CatalogBox>() }
        val vm = DiscoveryViewModel(SavedStateHandle(), catalog, repository)
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) { vm.refreshPublicStores() }
        vm.search("an nhien")
        vm.district("TP. Hồ Chí Minh")
        ready.complete(Unit)
        refresh.join()
        assertEquals("an nhien", vm.state.query)
        assertEquals("TP. Hồ Chí Minh", vm.state.district)
        assertEquals(listOf(store), vm.state.mapPresentation.publicStores)
    }
}
