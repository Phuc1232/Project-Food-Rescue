package vn.foodrescue.app.di

import vn.foodrescue.app.data.remote.SystemApi
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
import vn.foodrescue.app.data.repository.RemoteSystemRepository
import vn.foodrescue.app.data.repository.SystemRepository

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton fun client(): OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false) // Do not silently replay a consumed refresh token after a lost response.
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    @Provides @Singleton fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL).client(client)
        .addConverterFactory(GsonConverterFactory.create()).build()
    @Provides @Singleton fun api(retrofit: Retrofit): SystemApi = retrofit.create(SystemApi::class.java)
    @Provides @Singleton fun authApi(retrofit: Retrofit): vn.foodrescue.app.data.remote.AuthApi = retrofit.create(vn.foodrescue.app.data.remote.AuthApi::class.java)
    @Provides @Singleton fun tokenStore(store: vn.foodrescue.app.data.session.EncryptedSessionTokenStore): vn.foodrescue.app.data.session.SessionTokenStore = store
    @Provides @Singleton fun repository(api: SystemApi): SystemRepository = RemoteSystemRepository(api)
}
