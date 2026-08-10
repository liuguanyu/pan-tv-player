package com.baidu.tv.player.kt.auth

import app.cash.turbine.test
import com.baidu.tv.player.kt.model.DeviceCodeResponse
import com.baidu.tv.player.kt.model.TokenResponse
import com.baidu.tv.player.kt.network.FakeBaiduPanService
import com.baidu.tv.player.kt.util.MainCoroutineRule
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * LoginViewModel 单测：轮询状态变化（authorization_pending → 成功 / expired_token）。
 * 用 [FakeBaiduPanService] + [MainCoroutineRule]（UnconfinedTestDispatcher，控制 delay）。
 *
 * 断言终态而非每个中间态——StateFlow 会合并快速连续的更新，终态断言更稳健且更能反映行为。
 */
@RunWith(RobolectricTestRunner::class)
class LoginViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var fake: FakeBaiduPanService
    private lateinit var service: BaiduAuthService
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        fake = FakeBaiduPanService()
        val prefs = context.getSharedPreferences("vm_test_prefs", android.content.Context.MODE_PRIVATE)
        service = BaiduAuthService(context, fake, prefs)
        viewModel = LoginViewModel(service)
    }

    @Test
    fun startLogin_reachesAuthenticated_afterPollingSuccess() = runTest {
        fake.deviceCodeResponse = DeviceCodeResponse(deviceCode = "dc", userCode = "uc", verificationUrl = "http://v")
        // 第一次轮询 pending
        fake.tokenResponse = TokenResponse(error = "authorization_pending")

        viewModel.uiState.test {
            viewModel.startLogin()
            // pending 后推进时钟，期间切换为成功 token
            fake.tokenResponse = TokenResponse(accessToken = "at", refreshToken = "rt", expiresIn = 3600)
            advanceTimeBy(10_000L) // 超过 POLLING_INTERVAL(5s)

            // 收集直到终态
            var state = awaitItem()
            while (state !is LoginUiState.Authenticated && state !is LoginUiState.Error) {
                state = awaitItem()
            }
            assertTrue("expected Authenticated but was $state", state is LoginUiState.Authenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startLogin_emitsError_whenDeviceCodeFails() = runTest {
        fake.deviceCodeResponse = DeviceCodeResponse(error = "denied")
        viewModel.uiState.test {
            viewModel.startLogin()
            var state = awaitItem()
            while (state !is LoginUiState.Authenticated && state !is LoginUiState.Error) {
                state = awaitItem()
            }
            assertTrue(state is LoginUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startLogin_emitsError_whenExpired() = runTest {
        fake.deviceCodeResponse = DeviceCodeResponse(deviceCode = "dc", userCode = "uc", verificationUrl = "http://v")
        fake.tokenResponse = TokenResponse(error = "expired_token")
        viewModel.uiState.test {
            viewModel.startLogin()
            var state = awaitItem()
            while (state !is LoginUiState.Authenticated && state !is LoginUiState.Error) {
                state = awaitItem()
            }
            assertTrue(state is LoginUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun logout_emitsUnauthenticated() = runTest {
        viewModel.uiState.test {
            viewModel.logout()
            var state = awaitItem()
            while (state !is LoginUiState.Unauthenticated) {
                state = awaitItem()
            }
            assertTrue(state is LoginUiState.Unauthenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
