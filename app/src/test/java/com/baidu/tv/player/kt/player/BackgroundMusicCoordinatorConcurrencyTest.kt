package com.baidu.tv.player.kt.player

import app.cash.turbine.test
import com.baidu.tv.player.kt.repository.BgmSelection
import com.baidu.tv.player.kt.repository.PlayableUrlResolver
import com.baidu.tv.player.kt.ui.playback.PlaybackUiState
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * BGM 并发测试（Task 6.3）。
 *
 * 覆盖：
 * - A→B 切换时 A 仍在解析：只有 B 的结果驱动播放器。
 * - 解析期间清空选择：播放器停止，协程取消。
 * - 旧结果晚到：A 的迟到成功在 B 已启动后被丢弃。
 * - 失败重试：首次失败 → Failed，再次 setSelection → Ready。
 * - 取消传播：切换选择取消旧解析，CancellationException 透明传播。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundMusicCoordinatorConcurrencyTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val resolver: PlayableUrlResolver = mockk()
    private val player: FakeBackgroundAudioPlayer = FakeBackgroundAudioPlayer()

    private fun createCoordinator(): BackgroundMusicCoordinator =
        BackgroundMusicCoordinator(resolver, player)
            .withScope(mainCoroutineRule.scope)

    private val selectionA = BgmSelection(fsId = 100L, path = "/music/a.mp3", name = "a.mp3")
    private val selectionB = BgmSelection(fsId = 200L, path = "/music/b.mp3", name = "b.mp3")
    private val headers = mapOf("User-Agent" to "pan.baidu.com")

    @Test
    fun switchFromAtoB_whileAResolving_onlyBDrivesPlayer() = runTest {
        val deferredA = CompletableDeferred<String>()
        coEvery { resolver.resolve(100L, null, null) } coAnswers { deferredA.await() }
        coEvery { resolver.resolve(200L, null, null) } returns "http://b.com/music.mp3"

        val coordinator = createCoordinator()

        coordinator.state.test {
            assertEquals(BgmState.Disabled, awaitItem())
            coordinator.setSelection(selectionA, headers)
            assertEquals(BgmState.Resolving(100L), awaitItem())

            // A 仍 pending，切换到 B
            coordinator.setSelection(selectionB, headers)
            assertEquals(BgmState.Resolving(200L), awaitItem())

            // B 解析完成
            mainCoroutineRule.scope.advanceUntilIdle()
            assertEquals(BgmState.Ready(200L, "http://b.com/music.mp3"), awaitItem())

            // A 的迟到结果不应改变状态
            deferredA.complete("http://a.com/late.mp3")
            mainCoroutineRule.scope.advanceUntilIdle()
            // 状态仍为 B 的 Ready
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        // 播放器不应收到 A 的 URL
        val playCalls = player.calls.filterIsInstance<FakeBackgroundAudioPlayer.Call.Play>()
        assertTrue("no play call for A's url, calls=${player.calls}", playCalls.none { it.url == "http://a.com/late.mp3" })
    }

    @Test
    fun clearDuringResolve_playerStopped_jobCancelled() = runTest {
        val deferredA = CompletableDeferred<String>()
        coEvery { resolver.resolve(100L, null, null) } coAnswers { deferredA.await() }

        val coordinator = createCoordinator()

        coordinator.state.test {
            assertEquals(BgmState.Disabled, awaitItem())
            coordinator.setSelection(selectionA, headers)
            assertEquals(BgmState.Resolving(100L), awaitItem())

            // 清空选择
            coordinator.setSelection(null, headers)
            assertEquals(BgmState.Disabled, awaitItem())

            // A 迟到完成
            deferredA.complete("http://a.com/late.mp3")
            mainCoroutineRule.scope.advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        // 播放器应被 stop（清空选择触发 stop）
        assertTrue("expected Stop, calls=${player.calls}", player.calls.any { it is FakeBackgroundAudioPlayer.Call.Stop })
        // 不应有 A 的 play 调用
        val playCalls = player.calls.filterIsInstance<FakeBackgroundAudioPlayer.Call.Play>()
        assertTrue("no play call after clear, calls=${player.calls}", playCalls.none { it.url == "http://a.com/late.mp3" })
    }

    @Test
    fun oldResultLateAfterBStarted_discarded() = runTest {
        val deferredA = CompletableDeferred<String>()
        coEvery { resolver.resolve(100L, null, null) } coAnswers { deferredA.await() }
        coEvery { resolver.resolve(200L, null, null) } returns "http://b.com/music.mp3"

        val coordinator = createCoordinator()

        coordinator.setSelection(selectionA, headers)
        // 不等 A 完成，切换到 B
        coordinator.setSelection(selectionB, headers)
        mainCoroutineRule.scope.advanceUntilIdle()

        assertEquals(BgmState.Ready(200L, "http://b.com/music.mp3"), coordinator.state.value)

        // A 迟到
        deferredA.complete("http://a.com/late.mp3")
        mainCoroutineRule.scope.advanceUntilIdle()

        // 状态仍为 B
        assertEquals(BgmState.Ready(200L, "http://b.com/music.mp3"), coordinator.state.value)
    }

    @Test
    fun failureRetry_firstFailsThenSucceeds() = runTest {
        coEvery { resolver.resolve(100L, null, null) } throws java.io.IOException("fail") andThen
            "http://a.com/music.mp3"

        val coordinator = createCoordinator()

        // 首次：失败
        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()
        assertTrue("expected Failed, was ${coordinator.state.value}", coordinator.state.value is BgmState.Failed)

        // 重试 → 最终到达 Ready
        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()
        assertEquals(BgmState.Ready(100L, "http://a.com/music.mp3"), coordinator.state.value)
    }

    @Test
    fun selectionChange_cancelsOldResolve_cancellationPropagates() = runTest {
        var cancellationThrown = false
        coEvery { resolver.resolve(100L, null, null) } coAnswers {
            try {
                delay(10_000)
                "http://a.com/music.mp3"
            } catch (c: kotlinx.coroutines.CancellationException) {
                cancellationThrown = true
                throw c
            }
        }
        coEvery { resolver.resolve(200L, null, null) } returns "http://b.com/music.mp3"

        val coordinator = createCoordinator()

        coordinator.setSelection(selectionA, headers)
        // 切换到 B，取消 A
        coordinator.setSelection(selectionB, headers)
        mainCoroutineRule.scope.advanceUntilIdle()

        assertTrue("CancellationException should propagate from A's resolve", cancellationThrown)
        assertEquals(BgmState.Ready(200L, "http://b.com/music.mp3"), coordinator.state.value)
    }

    @Test
    fun release_cancelsResolve_andReleasesPlayer() = runTest {
        val deferredA = CompletableDeferred<String>()
        coEvery { resolver.resolve(100L, null, null) } coAnswers { deferredA.await() }

        val coordinator = createCoordinator()
        coordinator.setSelection(selectionA, headers)
        assertTrue(coordinator.state.value is BgmState.Resolving)

        coordinator.release()

        assertEquals(BgmState.Disabled, coordinator.state.value)
        assertTrue("player released, calls=${player.calls}", player.calls.any { it is FakeBackgroundAudioPlayer.Call.Release })

        // 迟到完成不改变状态
        deferredA.complete("http://a.com/late.mp3")
        mainCoroutineRule.scope.advanceUntilIdle()
        assertEquals(BgmState.Disabled, coordinator.state.value)
    }
}
