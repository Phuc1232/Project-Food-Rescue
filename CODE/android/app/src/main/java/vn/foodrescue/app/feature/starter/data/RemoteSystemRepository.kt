package vn.foodrescue.app.feature.starter.data

import java.io.IOException
import com.google.gson.JsonParseException
import javax.inject.Inject
import retrofit2.HttpException
import vn.foodrescue.app.core.network.SystemApi
import vn.foodrescue.app.feature.starter.domain.ConnectionResult
import vn.foodrescue.app.feature.starter.domain.SystemRepository

class RemoteSystemRepository @Inject constructor(private val api: SystemApi) : SystemRepository {
    override suspend fun checkConnection(): ConnectionResult = try {
        val result = api.status()
        if (result.status == "UP" && result.database == "UP" && !result.service.isNullOrBlank()) ConnectionResult.Connected(result.service)
        else ConnectionResult.Unavailable("Máy chủ hoặc cơ sở dữ liệu chưa sẵn sàng.")
    } catch (error: IOException) {
        ConnectionResult.Unavailable("Chưa kết nối được. Kiểm tra backend, địa chỉ API và mạng.")
    } catch (error: HttpException) {
        ConnectionResult.Unavailable("Máy chủ trả lỗi HTTP ${error.code()}. Vui lòng thử lại.")
    } catch (error: JsonParseException) {
        ConnectionResult.Unavailable("Dữ liệu máy chủ chưa đúng định dạng. Vui lòng thử lại.")
    }
}
