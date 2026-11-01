package vn.foodrescue.app.data.remote

import retrofit2.http.GET

interface SystemApi {
    @GET("api/v1/system/status") suspend fun status(): SystemStatusDto
}
data class SystemStatusDto(val service: String?, val status: String?, val database: String?)
