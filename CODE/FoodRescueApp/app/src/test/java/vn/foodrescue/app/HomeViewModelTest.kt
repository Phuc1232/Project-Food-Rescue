package vn.foodrescue.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import vn.foodrescue.app.data.repository.*
import vn.foodrescue.app.ui.home.*
import vn.foodrescue.app.data.repository.RemoteSystemRepository
import vn.foodrescue.app.data.remote.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test fun `missing service name is not a successful connection`() = runTest {
        val repository = RemoteSystemRepository(object : SystemApi { override suspend fun status() = SystemStatusDto(null, "UP", "UP") })
        assertTrue(repository.checkConnection() is ConnectionResult.Unavailable)
    }
    @Test fun `failed connection can be retried successfully`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var result: ConnectionResult = ConnectionResult.Unavailable("offline")
            val vm = HomeViewModel(object : SystemRepository { override suspend fun checkConnection() = result })
            advanceUntilIdle()
            assertEquals(HomeUiState.Error("offline"), vm.state.value)
            result = ConnectionResult.Connected("FoodRescue")
            vm.refresh(); advanceUntilIdle()
            assertEquals(HomeUiState.Ready("FoodRescue"), vm.state.value)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun `network failure becomes recoverable state`() = runTest {
        val repository = RemoteSystemRepository(object : SystemApi { override suspend fun status(): SystemStatusDto = throw IOException("offline") })
        assertTrue(repository.checkConnection() is ConnectionResult.Unavailable)
    }
    @Test fun `database down cannot display connected`() = runTest {
        val repository = RemoteSystemRepository(object : SystemApi { override suspend fun status() = SystemStatusDto("FoodRescue", "UP", "DOWN") })
        assertTrue(repository.checkConnection() is ConnectionResult.Unavailable)
    }
}
