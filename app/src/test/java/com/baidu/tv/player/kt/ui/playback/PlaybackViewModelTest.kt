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
import kotlinx.coroutines.delay
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
    fun recentPlayback_switchingItemPersistsHistoryWithoutReorderingSessionSnapshot() = runTest {
        val histories = listOf(
            history(id = 11, name = "a.mp4", fsId = 1),
            history(id = 12, name = "b.mp4", fsId = 2),
            history(id = 13, name = "c.mp4", fsId = 3),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(12) } returns MutableStateFlow(histories[1])
        histories.forEach { history ->
            coEvery { fileRepository.fetchFileDetail("token", history.fsId) } returns
                video(history.folderName!!, history.fsId, dlink = "https://d/${history.fsId}")
        }
        val vm = viewModel()

        vm.initializeFromHistory(12)
        advanceUntilIdle()
        assertEquals(listOf(1L, 2L, 3L), vm.uiState.value.files.map { it.fsId })
        assertEquals(1, vm.uiState.value.currentIndex)

        vm.playFromIndex(2)
        advanceUntilIdle()

        assertEquals(listOf(1L, 2L, 3L), vm.uiState.value.files.map { it.fsId })
        assertEquals(2, vm.uiState.value.currentIndex)
        assertEquals(3L, vm.uiState.value.currentFile?.fsId)
        coVerify { historyRepository.insert(match { it.fsId == 3L }) }
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
    fun initialize_reverseModeStartsFromLastItem() = runTest {
        val reverseMode = MutableStateFlow(PlayMode.REVERSE)
        every { settingsRepository.playMode } returns reverseMode
        val files = listOf(
            video("a.mp4", fsId = 1, dlink = "https://d/a"),
            video("b.mp4", fsId = 2, dlink = "https://d/b"),
            video("c.mp4", fsId = 3, dlink = "https://d/c"),
        )
        playlistCache.put("p", files)
        val vm = viewModel()

        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.currentIndex)
        assertEquals("c.mp4", vm.uiState.value.currentFile?.serverFilename)
        vm.playNext()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.currentIndex)
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

    // ------------------------------------------------------------------
    // Task 1.1: 最近播放会话表征测试
    // ------------------------------------------------------------------

    @Test
    fun initializeFromHistory_emptyHistory_emitsError() = runTest {
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(emptyList())
        val vm = viewModel()

        vm.events.test {
            vm.initializeFromHistory(1)
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is PlaybackUiEvent.ShowError)
            assertEquals("播放历史为空", (event as PlaybackUiEvent.ShowError).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun initializeFromHistory_fsIdLeqZero_filteredFromSnapshot() = runTest {
        val histories = listOf(
            history(id = 11, name = "a.mp4", fsId = 1),
            PlaybackHistory(
                id = 12, folderPath = "/movies/invalid", folderName = "invalid",
                mediaType = MediaType.VIDEO.code, fileCount = 1, fsId = 0,
            ),
            history(id = 13, name = "b.mp4", fsId = 2),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(11) } returns MutableStateFlow(histories[0])
        coEvery { fileRepository.fetchFileDetail("token", 1) } returns
            video("a.mp4", fsId = 1, dlink = "https://d/1")

        val vm = viewModel()
        vm.initializeFromHistory(11)
        advanceUntilIdle()

        // fsId=0 记录被过滤，快照中只有 2 条
        assertEquals(2, vm.uiState.value.files.size)
        assertEquals(listOf(1L, 2L), vm.uiState.value.files.map { it.fsId })
    }

    @Test
    fun initializeFromHistory_targetNotInRecentSnapshot_currentlyFallsBackToStart() = runTest {
        // 表征测试：被点击的历史记录 fsId 不在最近 100 条快照中时，
        // 当前行为回退到 resolveStartIndex（Phase 2.5 将改为显示错误且不播放）。
        val histories = listOf(
            history(id = 11, name = "a.mp4", fsId = 1),
            history(id = 12, name = "b.mp4", fsId = 2),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        // 点击的记录 fsId=999 不在快照中
        val clickedHistory = PlaybackHistory(
            id = 99, folderPath = "/movies/c.mp4", folderName = "c.mp4",
            mediaType = MediaType.VIDEO.code, fileCount = 1, fsId = 999,
        )
        every { historyRepository.getHistoryById(99) } returns MutableStateFlow(clickedHistory)
        coEvery { fileRepository.fetchFileDetail("token", 1) } returns
            video("a.mp4", fsId = 1, dlink = "https://d/1")

        val vm = viewModel()
        vm.initializeFromHistory(99)
        advanceUntilIdle()

        // 当前行为：回退到 start index 0（顺序模式）
        assertEquals(0, vm.uiState.value.currentIndex)
        assertEquals(1L, vm.uiState.value.currentFile?.fsId)
    }

    @Test
    fun initializeFromHistory_exactly100Items_allIncludedInSnapshot() = runTest {
        val histories = (1..100).map { i ->
            history(id = i.toLong(), name = "file$i.mp4", fsId = i.toLong())
        }
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(50) } returns MutableStateFlow(histories[49])
        histories.forEach { h ->
            coEvery { fileRepository.fetchFileDetail("token", h.fsId) } returns
                video(h.folderName!!, h.fsId, dlink = "https://d/${h.fsId}")
        }

        val vm = viewModel()
        vm.initializeFromHistory(50)
        advanceUntilIdle()

        assertEquals(100, vm.uiState.value.files.size)
        assertEquals(49, vm.uiState.value.currentIndex)
        assertEquals(50L, vm.uiState.value.currentFile?.fsId)
    }

    // ------------------------------------------------------------------
    // Task 1.4: 快速连续选择媒体并发测试
    // ------------------------------------------------------------------

    @Test
    fun preloadNextFile_doesNotChangeCurrentIndexOrCurrentFile() = runTest {
        val files = listOf(
            video("a.mp4", fsId = 1, dlink = "https://d/a"),
            video("b.mp4", fsId = 2),
        )
        playlistCache.put("p", files)
        coEvery { fileRepository.fetchFileDetail("token", 2) } returns files[1].copy(dlink = "https://d/b")
        val vm = viewModel()
        vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
        advanceUntilIdle()

        val originalIndex = vm.uiState.value.currentIndex
        val originalFile = vm.uiState.value.currentFile

        vm.preloadNextFile()
        vm.awaitPreloadForTest()
        advanceUntilIdle()

        assertEquals(originalIndex, vm.uiState.value.currentIndex)
        assertEquals(originalFile, vm.uiState.value.currentFile)
    }

    @Test
    fun rapidSwitching_lateResolveEmitsStaleEvent_currentBehavior() = runTest {
        // 表征测试：A 解析较慢、用户切到 B 时，A 的迟到的解析结果仍会发出 PlayVideo(A)。
        // Phase 9 将用 generation 机制抑制旧请求的结果。
        val files = listOf(
            video("a.mp4", fsId = 1, dlink = null),
            video("b.mp4", fsId = 2, dlink = "https://d/b"),
        )
        playlistCache.put("p", files)
        coEvery { fileRepository.fetchFileDetail("token", 1) } coAnswers {
            delay(1_000)
            files[0].copy(dlink = "https://d/a")
        }

        val vm = viewModel()

        vm.events.test {
            vm.initialize("p", MediaType.VIDEO.code, "/movies", 0)
            // A 的 prepare 在 fetchFileDetail(1) 上 suspend（delay 1000）

            // 切到 B：B 有 dlink，立即解析成功并发出 PlayVideo(B)
            vm.playFromIndex(1)
            val eventB = awaitItem()
            assertTrue("expected PlayVideo for B", eventB is PlaybackUiEvent.PlayVideo)
            assertEquals(2L, (eventB as PlaybackUiEvent.PlayVideo).file.fsId)

            // advanceUntilIdle 后 A 的 delay 完成，A 的旧 prepare 发出 PlayVideo(A) —— 陈旧结果
            advanceUntilIdle()
            val staleEventA = awaitItem()
            assertTrue("expected stale PlayVideo for A", staleEventA is PlaybackUiEvent.PlayVideo)
            assertEquals(1L, (staleEventA as PlaybackUiEvent.PlayVideo).file.fsId)

            cancelAndIgnoreRemainingEvents()
        }
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

    private fun history(
        id: Long,
        name: String,
        fsId: Long,
    ) = PlaybackHistory(
        id = id,
        folderPath = "/movies/$name",
        folderName = name,
        mediaType = MediaType.VIDEO.code,
        fileCount = 1,
        lastPlayTime = id,
        createTime = id,
        fsId = fsId,
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
