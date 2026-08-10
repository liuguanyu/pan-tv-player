package com.baidu.tv.player.kt.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.baidu.tv.player.kt.model.DeviceCodeResponse
import com.baidu.tv.player.kt.model.TokenResponse
import com.baidu.tv.player.kt.network.FakeBaiduPanService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BaiduAuthService 单测：device_code 获取 → 轮询 → token 存储 → 刷新 → 登出。
 * 用 Robolectric 提供 Context + 明文 SharedPreferences（绕过 Keystore）。
 */
@RunWith(RobolectricTestRunner::class)
class BaiduAuthServiceTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var fake: FakeBaiduPanService
    private lateinit var service: BaiduAuthService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_auth_prefs", Context.MODE_PRIVATE)
        fake = FakeBaiduPanService()
        service = BaiduAuthService(context, fake, prefs)
    }

    @Test
    fun getDeviceCode_returnsResponse_whenNoError() = runTest {
        fake.deviceCodeResponse = DeviceCodeResponse(deviceCode = "dc", userCode = "uc", verificationUrl = "http://v")
        val resp = service.getDeviceCode()
        assertEquals("dc", resp.deviceCode)
        assertEquals("uc", resp.userCode)
    }

    @Test(expected = IllegalStateException::class)
    fun getDeviceCode_throws_whenError() = runTest {
        fake.deviceCodeResponse = DeviceCodeResponse(error = "denied")
        service.getDeviceCode()
        Unit
    }

    @Test
    fun pollDeviceCodeStatus_authenticated_whenTokenReturned() = runTest {
        fake.tokenResponse = TokenResponse(
            accessToken = "at", refreshToken = "rt", expiresIn = 3600, scope = "basic",
        )
        val result = service.pollDeviceCodeStatus("dc")
        assertEquals(BaiduAuthService.PollResult.AUTHENTICATED, result)
        // token 持久化
        assertEquals("at", prefs.getString("access_token", null))
        assertEquals("rt", prefs.getString("refresh_token", null))
        assertTrue(service.isAuthenticated())
    }

    @Test
    fun pollDeviceCodeStatus_pending_whenAuthorizationPending() = runTest {
        // suspend 方法在 pending 时返回带 error 的 TokenResponse（非抛异常路径）
        fake.tokenResponse = TokenResponse(error = "authorization_pending")
        val result = service.pollDeviceCodeStatus("dc")
        assertEquals(BaiduAuthService.PollResult.PENDING, result)
        assertFalse(service.isAuthenticated())
    }

    @Test
    fun pollDeviceCodeStatus_expired_whenExpiredToken() = runTest {
        fake.tokenResponse = TokenResponse(error = "expired_token")
        val result = service.pollDeviceCodeStatus("dc")
        assertEquals(BaiduAuthService.PollResult.EXPIRED, result)
    }

    @Test
    fun refreshToken_succeeds_whenTokenReturned() = runTest {
        // 先设置一个已存在的 refresh token
        prefs.edit().putString("refresh_token", "old-rt").apply()
        // 重新加载 authInfo
        service = BaiduAuthService(context, fake, prefs)
        fake.tokenResponse = TokenResponse(accessToken = "new-at", refreshToken = "new-rt", expiresIn = 3600)
        assertTrue(service.refreshToken())
        assertEquals("new-at", prefs.getString("access_token", null))
    }

    @Test
    fun refreshToken_fails_whenNoRefreshToken() = runTest {
        assertFalse(service.refreshToken())
    }

    @Test
    fun logout_clearsAuthInfo() = runTest {
        fake.tokenResponse = TokenResponse(accessToken = "at", refreshToken = "rt", expiresIn = 3600)
        service.pollDeviceCodeStatus("dc")
        assertTrue(service.isAuthenticated())
        service.logout()
        assertFalse(service.isAuthenticated())
        assertNull(prefs.getString("access_token", null))
    }

    @Test
    fun getDeviceId_generatesAndPersists() {
        val id1 = service.getDeviceId()
        assertNotNull(id1)
        val id2 = service.getDeviceId()
        assertEquals(id1, id2) // 持久化，二次读取一致
    }

    @Test
    fun isAuthenticated_false_whenNotLoggedIn() {
        assertFalse(service.isAuthenticated())
    }

    @Test
    fun isAuthenticated_false_whenTokenExpired() = runTest {
        fake.tokenResponse = TokenResponse(accessToken = "at", refreshToken = "rt", expiresIn = -1)
        service.pollDeviceCodeStatus("dc")
        assertFalse(service.isAuthenticated())
    }
}
