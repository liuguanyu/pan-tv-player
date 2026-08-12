package com.baidu.tv.player.kt.player

import app.cash.turbine.test
import com.baidu.tv.player.kt.repository.BgmSelection
import com.baidu.tv.player.kt.repository.PlayableUrlResolver
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * BgmState 状态转换测试（Task 6.1）。
 *
 * 验证 [BackgroundMusicCoordinator.state] 在各种输入下的状态机转换：
 * Disabled → Resolving → Ready / Failed → Resolving（重试）→ Disabled。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundMusicCoordinatorStateTest {

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
    fun initialState_isDisabled() {
        val coordinator = createCoordinator()
        assertEquals(BgmState.Disabled, coordinator.state.value)
    }

    @Test
    fun setSelection_valid_transitionsToResolving() = runTest {
        coEvery { resolver.resolve(100L, null, null) } returns "http://a.com/music.mp3"
        val coordinator = createCoordinator()

        // 用 Turbine 捕获 Resolving 状态（在 advanceUntilIdle 之前）
        coordinator.state.test {
            assertEquals(BgmState.Disabled, awaitItem())
            coordinator.setSelection(selectionA, headers)
            assertEquals(BgmState.Resolving(100L), awaitItem())
            // Ready 在 advanceUntilIdle 后到达
            mainCoroutineRule.scope.advanceUntilIdle()
            assertEquals(BgmState.Ready(100L, "http://a.com/music.mp3"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun resolving_succeeds_transitionsToReady() = runTest {
        coEvery { resolver.resolve(100L, null, null) } returns "http://a.com/music.mp3"
        val coordinator = createCoordinator()

        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()

        assertEquals(BgmState.Ready(100L, "http://a.com/music.mp3"), coordinator.state.value)
    }

    @Test
    fun resolving_fails_transitionsToFailed() = runTest {
        coEvery { resolver.resolve(100L, null, null) } throws java.io.IOException("network error")
        val coordinator = createCoordinator()

        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue("state should be Failed, was $state", state is BgmState.Failed)
        assertEquals(100L, (state as BgmState.Failed).fsId)
        assertTrue(state.error.contains("network error"))
    }

    @Test
    fun failed_retryBySetSelection_transitionsToResolvingThenReady() = runTest {
        // 第一次失败
        coEvery { resolver.resolve(100L, null, null) } throws java.io.IOException("fail") andThen
            "http://a.com/music.mp3"
        val coordinator = createCoordinator()

        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()
        assertTrue(coordinator.state.value is BgmState.Failed)

        // 重试：再次 setSelection 同一 fsId → 最终到达 Ready
        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()
        assertEquals(BgmState.Ready(100L, "http://a.com/music.mp3"), coordinator.state.value)
    }

    @Test
    fun ready_newSelection_transitionsToResolving() = runTest {
        coEvery { resolver.resolve(100L, null, null) } returns "http://a.com/music.mp3"
        coEvery { resolver.resolve(200L, null, null) } returns "http://b.com/music.mp3"
        val coordinator = createCoordinator()

        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()
        assertEquals(BgmState.Ready(100L, "http://a.com/music.mp3"), coordinator.state.value)

        // 切换到新选择
        coordinator.state.test {
            assertEquals(BgmState.Ready(100L, "http://a.com/music.mp3"), awaitItem())
            coordinator.setSelection(selectionB, headers)
            assertEquals(BgmState.Resolving(200L), awaitItem())
            mainCoroutineRule.scope.advanceUntilIdle()
            assertEquals(BgmState.Ready(200L, "http://b.com/music.mp3"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anyState_clearSelection_transitionsToDisabled() = runTest {
        coEvery { resolver.resolve(100L, null, null) } returns "http://a.com/music.mp3"
        val coordinator = createCoordinator()

        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()
        assertTrue(coordinator.state.value is BgmState.Ready)

        coordinator.setSelection(null, headers)
        assertEquals(BgmState.Disabled, coordinator.state.value)
    }

    @Test
    fun disabled_clearSelection_staysDisabled() {
        val coordinator = createCoordinator()
        coordinator.setSelection(null, headers)
        assertEquals(BgmState.Disabled, coordinator.state.value)
    }

    @Test
    fun ready_sameSelection_doesNotReResolve() = runTest {
        coEvery { resolver.resolve(100L, null, null) } returns "http://a.com/music.mp3"
        val coordinator = createCoordinator()

        coordinator.setSelection(selectionA, headers)
        mainCoroutineRule.scope.advanceUntilIdle()
        assertEquals(BgmState.Ready(100L, "http://a.com/music.mp3"), coordinator.state.value)

        // 再次设置同一选择 → 仍为 Ready，不重新解析
        coordinator.setSelection(selectionA, headers)
        assertEquals(BgmState.Ready(100L, "http://a.com/music.mp3"), coordinator.state.value)
    }
}
