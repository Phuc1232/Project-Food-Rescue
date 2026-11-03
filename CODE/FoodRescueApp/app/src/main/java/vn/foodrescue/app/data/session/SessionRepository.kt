package vn.foodrescue.app.data.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import vn.foodrescue.app.data.remote.*
import javax.inject.Inject
import javax.inject.Singleton

class SessionExpiredException : Exception("Session expired")

@Singleton
class SessionRepository @Inject constructor(val api: AuthApi, private val store: SessionTokenStore) {
    // Serialize refreshes, logout and profile writes so one device never reuses its rotated refresh token.
    private val mutex = Mutex()

    suspend fun restore(): AccountProfile? = withContext(Dispatchers.IO) {
        if (store.read() == null) return@withContext null
        try { profile() } catch (_: SessionExpiredException) { null }
    }
    suspend fun login(email: String, password: String): AccountProfile = withContext(Dispatchers.IO) {
        mutex.withLock {
            val result = api.login(LoginBody(email.trim(), password))
            save(result)
            result.user
        }
    }
    suspend fun profile(): AccountProfile = authenticated { api.me(it) }
    suspend fun update(name: String, phone: String): AccountProfile = authenticated { api.updateMe(it, ProfileBody(name.trim(), phone.trim())) }
    suspend fun logout() {
        authenticated { api.logout(it); store.clear() }
    }
    private suspend fun <T> authenticated(block: suspend (String) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            var tokens = store.read() ?: throw SessionExpiredException()
            if (tokens.expiresAtMillis <= System.currentTimeMillis() + 30_000) tokens = refresh(tokens)
            try { block("Bearer ${tokens.accessToken}") }
            catch (error: HttpException) {
                if (error.code() != 401) throw error
                tokens = refresh(tokens)
                try { block("Bearer ${tokens.accessToken}") }
                catch (again: HttpException) {
                    if (again.code() == 401) { store.clear(); throw SessionExpiredException() }
                    throw again
                }
            }
        }
    }
    private suspend fun refresh(previous: SessionTokens): SessionTokens = try {
        val result = api.refresh(RefreshBody(previous.refreshToken))
        save(result)
    } catch (error: HttpException) {
        if (error.code() == 401) { store.clear(); throw SessionExpiredException() }
        throw error
    }
    private fun save(result: SessionResult): SessionTokens = SessionTokens(result.accessToken, result.refreshToken,
        System.currentTimeMillis() + result.expiresInSeconds * 1000).also(store::save)
}
