package com.baidu.tv.player.kt.ui.playback

import app.cash.turbine.test
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.repository.FileRepository
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.repository.PlaylistRepository
import com.baidu.tv.player.kt.repository.SettingsRepository
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import com.baidu.tv.player.kt.util.MainCoroutineRule
import com.baidu.tv.player.kt.location.LocationExtractionService
import com.baidu.tv.player.kt.util.PlaylistCache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var playlistCache: PlaylistCache
    private lateinit var authService: BaiduAuthService
    private lateinit var fileRepository: FileRepository
    private lateinit var historyRepository: PlaybackHistoryRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var locationExtractionService: LocationExtractionService

    @Before
    fun setUp() {
        playlistCache = PlaylistCache()
        authService = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        locationExtractionService = mockk(relaxed = true)
        every { settingsRepository.playMode } returns MutableStateFlow(PlayMode.SEQUENTIAL)
        every { settingsRepository.imageEffect } returns MutableStateFlow(ImageEffect.FADE)
        every { settingsRepository.backgroundMode } returns MutableStateFlow(ImageBackgroundMode.DOMINANT_COLOR)
        every { settingsRepository.imageDisplayDurationMs } returns MutableStateFlow(10_000)
        every { settingsRepository.imageTransitionDurationMs } returns MutableStateFlow(1_000)
        every { settingsRepository.showLocation } returns MutableStateFlow(true)
        every { settingsRepository.showCounter } returns MutableStateFlow(true)
        every { settingsRepository.showCaptureTime } returns MutableStateFlow(true)
        coEvery { locationExtractionService.extractLocation(any(), any()) } returns null
        coEvery { locationExtractionService.extractCaptureTime(any(), any()) } returns null
        every { authService.getAccessToken() } returns "token"
        coEvery { historyRepository.insert(any()) } returns Unit
    }

    @Test
    fun initialize_emitsPlayVideoAndPersistsHistory() = runTest {
        val files = listOf(video("a.mp4", fsId = 1, dlink = "https://d/a"), video("b.mp4", fsId = 2, dlink = "https://d/b"))
        playlistCache.put("p", files)
        val historySlot = slot<PlaybackHistory>()
        coEvery { historyRepository.insert(capture(historySlot)) } returns Unit
        val vm = viewModel()

        vm.events.test {
            vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
            val event = awaitItem()
            assertTrue(event is PlaybackUiEvent.PlayVideo)
            val playVideo = event as PlaybackUiEvent.PlayVideo
            assertEquals("https://d/a?access_token=token", playVideo.url)
            advanceUntilIdle()
            assertEquals("/movies/a.mp4", historySlot.captured.folderPath)
            assertEquals("a.mp4", historySlot.captured.folderName)
            assertEquals(1, historySlot.captured.fileCount)
            assertEquals(1L, historySlot.captured.fsId)
            assertEquals("/movies", historySlot.captured.sourceFolderPath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun playNextAndPrevious_followSequentialOrder() = runTest {
        val files = listOf(video("a.mp4", fsId = 1), video("b.mp4", fsId = 2), video("c.mp4", fsId = 3))
        playlistCache.put("p", files)
        coEvery { fileRepository.fetchFileDetail("token", 1) } returns files[0].copy(dlink = "https://d/a")
        coEvery { fileRepository.fetchFileDetail("token", 2) } returns files[1].copy(dlink = "https://d/b")
        coEvery { fileRepository.fetchFileDetail("token", 3) } returns files[2].copy(dlink = "https://d/c")
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 1)
        advanceUntilIdle()

        vm.playNext()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.currentIndex)
        assertEquals("c.mp4", vm.uiState.value.currentFile?.serverFilename)

        vm.playPrevious()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.currentIndex)
        assertEquals("b.mp4", vm.uiState.value.currentFile?.serverFilename)
    }

    @Test
    fun preloadNextFile_usesMutexAndCachesDlink() = runTest {
        val files = listOf(video("a.mp4", fsId = 1, dlink = "https://d/a"), video("b.mp4", fsId = 2))
        playlistCache.put("p", files)
        coEvery { fileRepository.fetchFileDetail("token", 2) } returns files[1].copy(dlink = "https://d/b")
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        vm.preloadNextFile()
        vm.preloadNextFile()
        vm.awaitPreloadForTest()
        advanceUntilIdle()

        coVerify(exactly = 1) { fileRepository.fetchFileDetail("token", 2) }
        vm.playNext()
        advanceUntilIdle()
        assertEquals("https://d/b?access_token=token", vm.uiState.value.preparedUrl)
    }

    @Test
    fun cyclePlayMode_reachesSingleModeAndNextKeepsSameIndex() = runTest {
        val files = listOf(video("a.mp4", fsId = 1, dlink = "https://d/a"), video("b.mp4", fsId = 2, dlink = "https://d/b"))
        playlistCache.put("p", files)
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        vm.cyclePlayMode() // RANDOM
        vm.cyclePlayMode() // SINGLE
        assertEquals(PlayMode.SINGLE, vm.uiState.value.playMode)
        vm.playNext()
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.currentIndex)
    }

    @Test
    fun imageAutoNext_notScheduledUntilContentReady() = runTest {
        val files = listOf(image("a.jpg", fsId = 1, dlink = "https://d/a"), image("b.jpg", fsId = 2, dlink = "https://d/b"))
        playlistCache.put("p", files)
        val vm = viewModel()
        vm.initialize("p", MediaType.IMAGE.code, "/photos", 0)
        advanceUntilIdle()

        // 内容尚未就绪：即便超过展示时长（10s）也不应自动切换到下一张。
        assertFalse(vm.uiState.value.contentReady)
        advanceTimeBy(20_000)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    @Test
    fun imageAutoNext_scheduledAfterContentReady() = runTest {
        val files = listOf(image("a.jpg", fsId = 1, dlink = "https://d/a"), image("b.jpg", fsId = 2, dlink = "https://d/b"))
        playlistCache.put("p", files)
        val vm = viewModel()
        vm.initialize("p", MediaType.IMAGE.code, "/photos", 0)
        advanceUntilIdle()

        // 图片渲染完成后置就绪并启动计时；到达展示时长后自动切换到下一张。
        vm.notifyContentReady()
        assertTrue(vm.uiState.value.contentReady)
        advanceTimeBy(10_001)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.currentIndex)
    }

    @Test
    fun videoContentReady_doesNotScheduleAutoNext() = runTest {
        val files = listOf(video("a.mp4", fsId = 1, dlink = "https://d/a"), video("b.mp4", fsId = 2, dlink = "https://d/b"))
        playlistCache.put("p", files)
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        // 视频就绪只置 contentReady，不受图片倒计时影响：超时后仍停留在当前视频。
        vm.notifyContentReady()
        assertTrue(vm.uiState.value.contentReady)
        advanceTimeBy(20_000)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    private fun viewModel() = PlaybackViewModel(
        settingsRepository = settingsRepository,
        playlistCache = playlistCache,
        authService = authService,
        fileRepository = fileRepository,
        historyRepository = historyRepository,
        playlistRepository = playlistRepository,
        locationExtractionService = locationExtractionService,
    )

    private fun video(
        name: String,
        fsId: Long,
        dlink: String? = null,
    ) = FileInfo(
        fsId = fsId,
        path = "/movies/$name",
        serverFilename = name,
        category = 1,
        dlink = dlink,
    )

    private fun image(
        name: String,
        fsId: Long,
        dlink: String? = null,
    ) = FileInfo(
        fsId = fsId,
        path = "/photos/$name",
        serverFilename = name,
        category = 3,
        dlink = dlink,
    )
}
