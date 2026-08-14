package com.baidu.tv.player.kt.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.location.LocationExtractionService
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.repository.SettingsRepository
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SettingsViewModel] 测试（对应 tasks.md 7.9）。
 *
 * 使用真实 [SettingsRepository]（Robolectric SharedPreferences）验证设置写入
 * 反映到 uiState；地点识别测试入口以 mock 的 [LocationExtractionService] 验证事件回传。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var repository: SettingsRepository
    private lateinit var locationService: LocationExtractionService
    private lateinit var authService: BaiduAuthService

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val prefs = context.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE)
        repository = SettingsRepository(prefs)
        locationService = mockk(relaxed = true)
        authService = mockk(relaxed = true)
    }

    private fun newViewModel() = SettingsViewModel(repository, locationService, authService)

    @Test
    fun logout_clearsAuthentication() {
        newViewModel().logout()

        verify(exactly = 1) { authService.logout() }
    }

    @Test
    fun uiState_reflectsRepositoryDefaults() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            val state = awaitItem()
            assertEquals(PlayMode.SEQUENTIAL, state.playMode)
            assertEquals(ImageEffect.FADE, state.imageEffect)
            assertEquals(ImageBackgroundMode.DOMINANT_COLOR, state.backgroundMode)
        }
    }

    @Test
    fun setPlayMode_updatesUiStateAndPersists() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            awaitItem() // 初始
            vm.setPlayMode(PlayMode.RANDOM)
            assertEquals(PlayMode.RANDOM, awaitItem().playMode)
        }
        assertEquals(PlayMode.RANDOM, repository.playMode.value)
    }

    @Test
    fun setShowLocation_updatesUiState() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            awaitItem()
            vm.setShowLocation(false)
            assertFalse(awaitItem().showLocation)
        }
    }

    @Test
    fun setShowCaptureTime_updatesUiState() = runTest {
        val vm = newViewModel()
        vm.uiState.test {
            awaitItem()
            vm.setShowCaptureTime(false)
            assertFalse(awaitItem().showCaptureTime)
        }
    }

    @Test
    fun testLocationExtraction_emitsResultEvent() = runTest {
        coEvery { locationService.extractLocation(any(), any(), any()) } returns "北京市"
        val vm = newViewModel()
        vm.events.test {
            vm.testLocationExtraction("http://example.com/p.jpg", isVideo = false)
            advanceUntilIdle()
            val event = awaitItem()
            assertEquals(SettingsUiEvent.LocationTestResult("北京市"), event)
        }
    }

    @Test
    fun testLocationExtraction_nullResultStillEmits() = runTest {
        coEvery { locationService.extractLocation(any(), any(), any()) } returns null
        val vm = newViewModel()
        vm.events.test {
            vm.testLocationExtraction("http://example.com/p.jpg", isVideo = true)
            advanceUntilIdle()
            assertEquals(SettingsUiEvent.LocationTestResult(null), awaitItem())
        }
    }
}
