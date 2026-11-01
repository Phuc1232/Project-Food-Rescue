package vn.foodrescue.app.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import vn.foodrescue.app.BuildConfig
import vn.foodrescue.app.feature.starter.data.RemoteSystemRepository
import vn.foodrescue.app.feature.starter.domain.SystemRepository

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    @Provides @Singleton fun api(client: OkHttpClient): SystemApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build().create(SystemApi::class.java)
    @Provides @Singleton fun repository(api: SystemApi): SystemRepository = RemoteSystemRepository(api)
}
