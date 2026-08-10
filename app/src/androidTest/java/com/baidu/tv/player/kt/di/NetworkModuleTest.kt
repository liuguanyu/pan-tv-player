package com.baidu.tv.player.kt.di

import com.baidu.tv.player.kt.network.BaiduPanService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import javax.inject.Inject

/**
 * RetrofitClient Hilt Module 绑定验证：双 BaseUrl Retrofit + BaiduPanService 注入。
 */
@HiltAndroidTest
class NetworkModuleTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject @PanApi lateinit var panApiRetrofit: Retrofit
    @Inject @OAuthApi lateinit var oauthRetrofit: Retrofit
    @Inject lateinit var baiduPanService: BaiduPanService

    @Test
    fun providesBothRetrofitInstancesWithDifferentBaseUrls() {
        hiltRule.inject()
        assertNotNull(panApiRetrofit)
        assertNotNull(oauthRetrofit)
        assertEquals(
            "https://pan.baidu.com/rest/2.0/",
            panApiRetrofit.baseUrl().toString(),
        )
        assertEquals(
            "https://openapi.baidu.com/oauth/2.0/",
            oauthRetrofit.baseUrl().toString(),
        )
    }

    @Test
    fun providesBaiduPanService() {
        hiltRule.inject()
        assertNotNull(baiduPanService)
    }
}
