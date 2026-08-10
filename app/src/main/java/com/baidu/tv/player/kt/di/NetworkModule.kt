package com.baidu.tv.player.kt.di

import com.baidu.tv.player.kt.network.ApiConstants
import com.baidu.tv.player.kt.network.BaiduPanService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络层 Hilt Module：提供 OkHttpClient + 双 BaseUrl Retrofit + BaiduPanService。
 * 替代 Java 版 [com.baidu.tv.player.network.RetrofitClient] 的手动双检锁单例。
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    .header("User-Agent", "pan.baidu.com")
                    .method(original.method, original.body)
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(ApiConstants.CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(ApiConstants.READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(ApiConstants.WRITE_TIMEOUT, TimeUnit.MILLISECONDS)
            .build()
    }

    @Provides
    @Singleton
    @PanApi
    fun providePanApiRetrofit(client: OkHttpClient): Retrofit =
        createRetrofit(ApiConstants.PAN_API_BASE_URL, client)

    @Provides
    @Singleton
    @OAuthApi
    fun provideOAuthRetrofit(client: OkHttpClient): Retrofit =
        createRetrofit(ApiConstants.OAUTH_BASE_URL, client)

    /** 百度网盘文件 API 服务，挂在 pan.baidu.com/rest/2.0/。 */
    @Provides
    @Singleton
    @PanApi
    fun provideBaiduPanService(@PanApi retrofit: Retrofit): BaiduPanService =
        retrofit.create(BaiduPanService::class.java)

    /** 百度 OAuth 服务，挂在 openapi.baidu.com/oauth/2.0/。 */
    @Provides
    @Singleton
    @OAuthApi
    fun provideBaiduOAuthService(@OAuthApi retrofit: Retrofit): BaiduPanService =
        retrofit.create(BaiduPanService::class.java)

    private fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
