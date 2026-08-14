package com.baidu.tv.player.kt.ui.playback

import app.cash.turbine.test
import com.baidu.tv.player.kt.location.LocationExtractionService
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.repository.SettingsRepository
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Phase 12.1 / 12.2: ViewModel 状态转换编排测试。
 *
 * 使用 mockk 模拟三个协调器（SessionFactory、QueueNavigator、PreparationCoordinator），
 * 验证 ViewModel 仅做状态编排：
 * - Idle → Loading → Ready/Failed
 * - A → B 切换
 * - retry
 * - contentReady
 * - 清理/取消
 *
 * 协调器内部逻辑由各自的单测覆盖，此处只验证 ViewModel 是否正确委托和更新 UiState。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackViewModelOrchestrationTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var historyRepository: PlaybackHistoryRepository
    private lateinit var locationExtractionService: LocationExtractionService
    private lateinit var sessionFactory: PlaybackSessionFactory
    private lateinit var queueNavigator: PlaybackQueueNavigator
    private lateinit var preparationCoordinator: MediaPreparationCoordinator

    /** 捕获 prepare 调用时的 callback 和 scope，供测试手动触发成功/失败。 */
    private var prepareCallback: PrepareCallback? = null
    private var prepareScope: CoroutineScope? = null
    private var prepareGeneration: Long = 0L

    @Before
    fun setUp() {
        settingsRepository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        locationExtractionService = mockk(relaxed = true)
        sessionFactory = mockk(relaxed = true)
        queueNavigator = mockk(relaxed = true)
        preparationCoordinator = mockk(relaxed = true)

        every { settingsRepository.playMode } returns MutableStateFlow(PlayMode.SEQUENTIAL)
        every { settingsRepository.imageEffect } returns MutableStateFlow(ImageEffect.FADE)
        every { settingsRepository.backgroundMode } returns MutableStateFlow(ImageBackgroundMode.DOMINANT_COLOR)
        every { settingsRepository.imageDisplayDurationMs } returns MutableStateFlow(10_000)
        every { settingsRepository.imageTransitionDurationMs } returns MutableStateFlow(1_000)
        every { settingsRepository.showLocation } returns MutableStateFlow(true)
        every { settingsRepository.showCounter } returns MutableStateFlow(true)
        every { settingsRepository.showCaptureTime } returns MutableStateFlow(true)
        coEvery { locationExtractionService.extractLocation(any(), any(), any()) } returns null
        coEvery { locationExtractionService.extractCaptureTime(any(), any(), any()) } returns null

        // 捕获 prepare 调用的参数
        every { preparationCoordinator.nextGeneration() } answers { prepareGeneration++ }
        every { preparationCoordinator.prepare(any(), any(), any(), any(), any()) } answers {
            prepareCallback = arg(4)
            prepareScope = arg(2)
        }
    }

    private fun viewModel() = PlaybackViewModel(
        historyRepository = historyRepository,
        settingsRepository = settingsRepository,
        locationExtractionService = locationExtractionService,
        sessionFactory = sessionFactory,
        queueNavigator = queueNavigator,
        preparationCoordinator = preparationCoordinator,
    )

    private fun video(name: String, fsId: Long) = FileInfo(
        fsId = fsId,
        path = "/movies/$name",
        serverFilename = name,
        category = 1,
    )

    private fun image(name: String, fsId: Long) = FileInfo(
        fsId = fsId,
        path = "/photos/$name",
        serverFilename = name,
        category = 3,
    )

    private fun session(files: List<FileInfo>, startIndex: Int = 0) = PlaybackSession(
        source = PlaybackSource.DIRECTORY_CACHE,
        playlistId = "test",
        items = files,
        folderPath = "/movies",
        title = "movies",
        startIndex = startIndex,
        mediaType = MediaType.VIDEO.code,
        sourcePlaylistId = null,
        sourceFolderPath = "/movies",
    )

    // ------------------------------------------------------------------
    // Idle → Loading → Ready
    // ------------------------------------------------------------------

    @Test
    fun idle_to_loading_to_ready_onInitialize() = runTest {
        val files = listOf(video("a.mp4", 1), video("b.mp4", 2))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        every { queueNavigator.nextIndex(any(), any(), any(), any()) } returns 1
        val vm = viewModel()

        // Idle
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.files.isEmpty())

        // initialize → Loading
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(2, vm.uiState.value.files.size)
        assertEquals(0, vm.uiState.value.currentIndex)

        // Coordinator prepare 回调成功 → Ready
        prepareCallback!!.onPrepareSuccess(files[0], "https://play/a")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("https://play/a", vm.uiState.value.preparedUrl)
        assertTrue(vm.uiState.value.isPlaying)
        assertNull(vm.uiState.value.errorMessage)
    }

    // ------------------------------------------------------------------
    // Idle → Loading → Failed
    // ------------------------------------------------------------------

    @Test
    fun idle_to_loading_to_failed_onPrepareFailure() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()

        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        assertTrue(vm.uiState.value.isLoading)

        prepareCallback!!.onPrepareFailure(files[0], "网络错误")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("网络错误", vm.uiState.value.errorMessage)
        assertFalse(vm.uiState.value.isPlaying)
    }

    @Test
    fun idle_to_failed_onSessionException() = runTest {
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } throws
            PlaybackSessionException("播放列表为空")
        val vm = viewModel()

        vm.events.test {
            vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
            val event = awaitItem()
            assertTrue(event is PlaybackUiEvent.ShowError)
            assertEquals("播放列表为空", (event as PlaybackUiEvent.ShowError).message)
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.files.isEmpty())
    }

    // ------------------------------------------------------------------
    // A → B 切换
    // ------------------------------------------------------------------

    @Test
    fun a_to_b_switch_updatesCurrentIndexAndFile() = runTest {
        val files = listOf(video("a.mp4", 1), video("b.mp4", 2))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        every { queueNavigator.nextIndex(2, 0, PlayMode.SEQUENTIAL, true) } returns 1
        every { queueNavigator.nextIndex(2, 1, PlayMode.SEQUENTIAL, true) } returns 0
        val vm = viewModel()

        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        prepareCallback!!.onPrepareSuccess(files[0], "https://play/a")
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.currentIndex)

        // 切到 B
        vm.playNext()
        assertEquals(1, vm.uiState.value.currentIndex)
        assertEquals(2L, vm.uiState.value.currentFile?.fsId)
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.preparedUrl)

        // B prepare 成功
        prepareCallback!!.onPrepareSuccess(files[1], "https://play/b")
        advanceUntilIdle()
        assertEquals("https://play/b", vm.uiState.value.preparedUrl)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun a_to_b_switch_resetsContentReadyAndProgress() = runTest {
        val files = listOf(video("a.mp4", 1), video("b.mp4", 2))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        every { queueNavigator.nextIndex(2, 0, PlayMode.SEQUENTIAL, true) } returns 1
        val vm = viewModel()

        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        prepareCallback!!.onPrepareSuccess(files[0], "https://play/a")
        advanceUntilIdle()
        vm.updateProgress(5000, 10000)
        vm.notifyContentReady()
        assertTrue(vm.uiState.value.contentReady)
        assertEquals(5000L, vm.uiState.value.positionMs)

        vm.playNext()
        assertFalse(vm.uiState.value.contentReady)
        assertEquals(0L, vm.uiState.value.positionMs)
        assertEquals(0L, vm.uiState.value.durationMs)
    }

    // ------------------------------------------------------------------
    // Retry
    // ------------------------------------------------------------------

    @Test
    fun retry_reloadsCurrentFile() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()

        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)

        // 第一次 prepare 失败
        prepareCallback!!.onPrepareFailure(files[0], "网络错误")
        advanceUntilIdle()
        assertEquals("网络错误", vm.uiState.value.errorMessage)

        // retry → 重新 Loading → 成功
        vm.retryCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMessage)

        prepareCallback!!.onPrepareSuccess(files[0], "https://play/a")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("https://play/a", vm.uiState.value.preparedUrl)
        assertNull(vm.uiState.value.errorMessage)
    }

    @Test
    fun retry_doesNothing_whenNoCurrentFile() = runTest {
        val vm = viewModel()
        vm.retryCurrent()
        // 不崩溃，状态不变
        assertFalse(vm.uiState.value.isLoading)
    }

    // ------------------------------------------------------------------
    // contentReady
    // ------------------------------------------------------------------

    @Test
    fun contentReady_setsFlagTrue() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.contentReady)
        vm.notifyContentReady()
        assertTrue(vm.uiState.value.contentReady)
    }

    @Test
    fun contentReady_isIdempotent() = runTest {
        val files = listOf(image("a.jpg", 1), image("b.jpg", 2))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        every { queueNavigator.nextIndex(2, 0, PlayMode.SEQUENTIAL, true) } returns 1
        val vm = viewModel()
        vm.initialize("p", MediaType.IMAGE.code, "/photos", 0)
        advanceUntilIdle()

        vm.notifyContentReady()
        assertTrue(vm.uiState.value.contentReady)
        // 再次调用不应重复启动计时或改变状态
        vm.notifyContentReady()
        assertTrue(vm.uiState.value.contentReady)
    }

    @Test
    fun contentReady_image_schedulesAutoNext() = runTest {
        val files = listOf(image("a.jpg", 1), image("b.jpg", 2))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        every { queueNavigator.nextIndex(2, 0, PlayMode.SEQUENTIAL, true) } returns 1
        val vm = viewModel()
        vm.initialize("p", MediaType.IMAGE.code, "/photos", 0)
        // 模拟 prepare 成功以设置 isPlaying = true
        prepareCallback!!.onPrepareSuccess(files[0], "https://play/a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isPlaying)

        vm.notifyContentReady()
        // 推进超过展示时长（10s）→ 自动切到下一张
        advanceTimeBy(10_001)
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.currentIndex)
    }

    @Test
    fun contentReady_video_doesNotScheduleAutoNext() = runTest {
        val files = listOf(video("a.mp4", 1), video("b.mp4", 2))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        every { queueNavigator.nextIndex(2, 0, PlayMode.SEQUENTIAL, true) } returns 1
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        vm.notifyContentReady()
        advanceTimeBy(20_000)
        advanceUntilIdle()
        // 视频不自动切换
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    // ------------------------------------------------------------------
    // 清理/取消
    // ------------------------------------------------------------------

    @Test
    fun playFromIndex_outOfBounds_isIgnored() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        vm.playFromIndex(5)
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    @Test
    fun playNext_whenNavigatorReturnsNull_isIgnored() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        every { queueNavigator.nextIndex(any(), any(), any(), any()) } returns null
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        vm.playNext()
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    @Test
    fun initialize_whenFilesAlreadyLoaded_isIgnored() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()

        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        assertEquals(1, vm.uiState.value.files.size)

        // 第二次 initialize 应被忽略
        vm.initialize("p2", MediaType.VIDEO.code, "/movies2", 0)
        assertEquals(1, vm.uiState.value.files.size)
        assertEquals("test", vm.uiState.value.playlistId)
    }

    // ------------------------------------------------------------------
    // PlayVideo / ShowImage 事件
    // ------------------------------------------------------------------

    @Test
    fun prepareSuccess_video_emitsPlayVideoEvent() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()

        vm.events.test {
            vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
            prepareCallback!!.onPrepareSuccess(files[0], "https://play/a")
            val event = awaitItem()
            assertTrue(event is PlaybackUiEvent.PlayVideo)
            assertEquals("https://play/a", (event as PlaybackUiEvent.PlayVideo).url)
            assertEquals(1L, event.file.fsId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun prepareSuccess_image_emitsShowImageEvent() = runTest {
        val files = listOf(image("a.jpg", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()

        vm.events.test {
            vm.initialize("p", MediaType.IMAGE.code, "/photos", 0)
            prepareCallback!!.onPrepareSuccess(files[0], "https://play/a")
            val event = awaitItem()
            assertTrue(event is PlaybackUiEvent.ShowImage)
            assertEquals("https://play/a", (event as PlaybackUiEvent.ShowImage).url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun prepareFailure_emitsShowErrorEvent() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()

        vm.events.test {
            vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
            prepareCallback!!.onPrepareFailure(files[0], "解析失败")
            val event = awaitItem()
            assertTrue(event is PlaybackUiEvent.ShowError)
            assertEquals("解析失败", (event as PlaybackUiEvent.ShowError).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ------------------------------------------------------------------
    // playPrevious 委托
    // ------------------------------------------------------------------

    @Test
    fun playPrevious_delegatesToNavigator() = runTest {
        val files = listOf(video("a.mp4", 1), video("b.mp4", 2), video("c.mp4", 3))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 2)
        every { queueNavigator.nextIndex(3, 2, PlayMode.SEQUENTIAL, false) } returns 1
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 2)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.currentIndex)

        vm.playPrevious()
        assertEquals(1, vm.uiState.value.currentIndex)
        assertEquals(2L, vm.uiState.value.currentFile?.fsId)
    }

    // ------------------------------------------------------------------
    // cyclePlayMode
    // ------------------------------------------------------------------

    @Test
    fun cyclePlayMode_rotatesAndPersists() = runTest {
        val files = listOf(video("a.mp4", 1))
        every { sessionFactory.fromDirectoryCache(any(), any(), any(), any(), any()) } returns
            session(files, startIndex = 0)
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        assertEquals(PlayMode.SEQUENTIAL, vm.uiState.value.playMode)
        vm.cyclePlayMode()
        assertEquals(PlayMode.RANDOM, vm.uiState.value.playMode)
        vm.cyclePlayMode()
        assertEquals(PlayMode.SINGLE, vm.uiState.value.playMode)
        vm.cyclePlayMode()
        assertEquals(PlayMode.REVERSE, vm.uiState.value.playMode)
        vm.cyclePlayMode()
        assertEquals(PlayMode.SEQUENTIAL, vm.uiState.value.playMode)
    }

    // ------------------------------------------------------------------
    // updateHistoryCover 委托
    // ------------------------------------------------------------------

    @Test
    fun updateHistoryCover_delegatesToRepository() = runTest {
        val vm = viewModel()
        vm.updateHistoryCover("/movies/a.mp4", "/cache/cover.jpg")
        advanceUntilIdle()
        coVerify { historyRepository.updateCover("/movies/a.mp4", "/cache/cover.jpg") }
    }

    @Test
    fun updateHistoryCover_blankPath_isIgnored() = runTest {
        val vm = viewModel()
        vm.updateHistoryCover("", "/cache/cover.jpg")
        advanceUntilIdle()
        coVerify(exactly = 0) { historyRepository.updateCover(any(), any()) }
    }
}
