package vn.foodrescue.app.data.repository

interface SystemRepository { suspend fun checkConnection(): ConnectionResult }
sealed interface ConnectionResult {
    data class Connected(val serviceName: String) : ConnectionResult
    data class Unavailable(val message: String) : ConnectionResult
}
