package com.baidu.tv.player.kt.ui.playback

import app.cash.turbine.test
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.repository.FileRepository
import com.baidu.tv.player.kt.repository.PlayableUrlResolver
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.repository.SettingsRepository
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import com.baidu.tv.player.kt.util.MainCoroutineRule
import com.baidu.tv.player.kt.util.PlaylistCache
import com.baidu.tv.player.kt.location.LocationExtractionService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * MediaPreparationCoordinator 并发与预加载测试（Task 9.1 & 9.2）。
 *
 * 9.1 generation 并发：
 * - A慢B快：只有 B 的 PlayVideo 发出。
 * - A晚到失败：A 的失败不显示错误，不中断 B。
 * - 切换取消：切到 B 后 A 的 resolve 协程被取消。
 * - retry 当前项：只发出一次 PlayVideo。
 *
 * 9.2 预加载：
 * - 去重：两次 preload 不重复请求。
 * - 复用：预加载后切换到该文件用缓存 URL，不重新请求。
 * - 失败隔离：预加载失败不影响当前媒体。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MediaPreparationCoordinatorTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var authService: BaiduAuthService
    private lateinit var fileRepository: FileRepository
    private lateinit var historyRepository: PlaybackHistoryRepository
    private lateinit var coordinator: MediaPreparationCoordinator

    @Before
    fun setUp() {
        authService = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        every { authService.getAccessToken() } returns "token"
        coordinator = MediaPreparationCoordinator(
            urlResolver = PlayableUrlResolver(authService, fileRepository),
            historyRepository = historyRepository,
            authService = authService,
            fileRepository = fileRepository,
        )
    }

    private fun video(name: String, fsId: Long, dlink: String? = null) = FileInfo(
        fsId = fsId,
        path = "/movies/$name",
        serverFilename = name,
        category = 1,
        dlink = dlink,
    )

    private fun image(name: String, fsId: Long, dlink: String? = null) = FileInfo(
        fsId = fsId,
        path = "/photos/$name",
        serverFilename = name,
        category = 3,
        dlink = dlink,
    )

    /**
     * 简单回调：记录所有成功/失败事件，用于验证 generation 控制是否正确。
     */
    private class RecordingCallback : PrepareCallback {
        val successes = mutableListOf<Pair<FileInfo, String>>()
        val failures = mutableListOf<Pair<FileInfo, String>>()

        override suspend fun onPrepareSuccess(file: FileInfo, url: String) {
            successes.add(file to url)
        }

        override suspend fun onPrepareFailure(file: FileInfo, message: String) {
            failures.add(file to message)
        }
    }

    private fun uiState(file: FileInfo): MutableStateFlow<PlaybackUiState> =
        MutableStateFlow(PlaybackUiState(currentFile = file, folderPath = "/movies"))

    // ------------------------------------------------------------------
    // Task 9.1: generation 并发测试
    // ------------------------------------------------------------------

    @Test
    fun slowA_fastB_onlyBCallbackFires() = runTest {
        val fileA = video("a.mp4", fsId = 1, dlink = null)
        val fileB = video("b.mp4", fsId = 2, dlink = "https://d/b")
        coEvery { fileRepository.fetchFileDetail("token", 1) } coAnswers {
            delay(1_000)
            fileA.copy(dlink = "https://d/a")
        }

        val callback = RecordingCallback()
        val state = uiState(fileA)

        // A: generation 1, slow resolve
        val genA = coordinator.nextGeneration()
        coordinator.prepare(fileA, genA, coroutineRule.scope, state, callback)

        // B: generation 2, fast resolve — cancels A
        val genB = coordinator.nextGeneration()
        coordinator.prepare(fileB, genB, coroutineRule.scope, state, callback)

        advanceUntilIdle()

        // Only B's success callback fires
        assertEquals(1, callback.successes.size)
        assertEquals(fileB, callback.successes[0].first)
        assertEquals(0, callback.failures.size)
    }

    @Test
    fun aLateFailure_doesNotShowError_afterBSucceeds() = runTest {
        val fileA = video("a.mp4", fsId = 1, dlink = null)
        val fileB = video("b.mp4", fsId = 2, dlink = "https://d/b")
        coEvery { fileRepository.fetchFileDetail("token", 1) } coAnswers {
            delay(1_000)
            throw RuntimeException("A 解析失败")
        }

        val callback = RecordingCallback()
        val state = uiState(fileA)

        val genA = coordinator.nextGeneration()
        coordinator.prepare(fileA, genA, coroutineRule.scope, state, callback)

        val genB = coordinator.nextGeneration()
        coordinator.prepare(fileB, genB, coroutineRule.scope, state, callback)

        advanceUntilIdle()

        // B succeeds, A's late failure is suppressed
        assertEquals(1, callback.successes.size)
        assertEquals(fileB, callback.successes[0].first)
        assertEquals(0, callback.failures.size)
    }

    @Test
    fun switchToB_cancelsAResolveCoroutine() = runTest {
        val fileA = video("a.mp4", fsId = 1, dlink = null)
        val fileB = video("b.mp4", fsId = 2, dlink = "https://d/b")
        val deferredA = CompletableDeferred<FileInfo>()
        coEvery { fileRepository.fetchFileDetail("token", 1) } coAnswers { deferredA.await() }

        val callback = RecordingCallback()
        val state = uiState(fileA)

        val genA = coordinator.nextGeneration()
        coordinator.prepare(fileA, genA, coroutineRule.scope, state, callback)

        // Switch to B before A resolves
        val genB = coordinator.nextGeneration()
        coordinator.prepare(fileB, genB, coroutineRule.scope, state, callback)
        advanceUntilIdle()

        // A's resolve is cancelled — completing it late should not trigger any callback
        deferredA.complete(fileA.copy(dlink = "https://d/a"))
        advanceUntilIdle()

        assertEquals(1, callback.successes.size)
        assertEquals(fileB, callback.successes[0].first)
    }

    @Test
    fun retryCurrent_emitsOnlyOnePlayVideo() = runTest {
        val fileA = video("a.mp4", fsId = 1, dlink = "https://d/a")

        val callback = RecordingCallback()
        val state = uiState(fileA)

        // First prepare
        val gen1 = coordinator.nextGeneration()
        coordinator.prepare(fileA, gen1, coroutineRule.scope, state, callback)
        advanceUntilIdle()
        assertEquals(1, callback.successes.size)

        // Retry: new generation, only one more success
        val gen2 = coordinator.nextGeneration()
        coordinator.prepare(fileA, gen2, coroutineRule.scope, state, callback)
        advanceUntilIdle()
        assertEquals(2, callback.successes.size)
    }

    // ------------------------------------------------------------------
    // Task 9.2: 预加载测试
    // ------------------------------------------------------------------

    @Test
    fun preloadNextFile_dedup_doesNotFetchTwice() = runTest {
        val fileB = video("b.mp4", fsId = 2, dlink = null)
        coEvery { fileRepository.fetchFileDetail("token", 2) } returns fileB.copy(dlink = "https://d/b")

        coordinator.preloadNextFile(coroutineRule.scope, fileB)
        coordinator.preloadNextFile(coroutineRule.scope, fileB)
        coordinator.awaitPreloadForTest()
        advanceUntilIdle()

        coVerify(exactly = 1) { fileRepository.fetchFileDetail("token", 2) }
    }

    @Test
    fun preloadNextFile_reuse_switchingToPreloadedUsesCachedUrl() = runTest {
        val fileA = video("a.mp4", fsId = 1, dlink = "https://d/a")
        val fileB = video("b.mp4", fsId = 2, dlink = null)
        coEvery { fileRepository.fetchFileDetail("token", 2) } returns fileB.copy(dlink = "https://d/b")

        // Preload B
        coordinator.preloadNextFile(coroutineRule.scope, fileB)
        coordinator.awaitPreloadForTest()
        advanceUntilIdle()

        // Now prepare B — should use cached URL, no new fetchFileDetail call
        val callback = RecordingCallback()
        val state = uiState(fileB)
        val gen = coordinator.nextGeneration()
        coordinator.prepare(fileB, gen, coroutineRule.scope, state, callback)
        advanceUntilIdle()

        assertEquals(1, callback.successes.size)
        assertEquals("https://d/b?access_token=token", callback.successes[0].second)
        // fetchFileDetail was called exactly once (during preload, not during prepare)
        coVerify(exactly = 1) { fileRepository.fetchFileDetail("token", 2) }
    }

    @Test
    fun preloadNextFile_failure_doesNotAffectCurrentMedia() = runTest {
        val fileA = video("a.mp4", fsId = 1, dlink = "https://d/a")
        val fileB = video("b.mp4", fsId = 2, dlink = null)
        coEvery { fileRepository.fetchFileDetail("token", 2) } throws RuntimeException("预加载失败")

        // Prepare A successfully
        val callback = RecordingCallback()
        val state = uiState(fileA)
        val genA = coordinator.nextGeneration()
        coordinator.prepare(fileA, genA, coroutineRule.scope, state, callback)
        advanceUntilIdle()
        assertEquals(1, callback.successes.size)

        // Preload B fails — should not affect A's state or trigger any callback
        coordinator.preloadNextFile(coroutineRule.scope, fileB)
        coordinator.awaitPreloadForTest()
        advanceUntilIdle()

        // A's success is still the only callback event
        assertEquals(1, callback.successes.size)
        assertEquals(0, callback.failures.size)
    }
}
