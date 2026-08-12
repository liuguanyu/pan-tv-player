package com.baidu.tv.player.kt.player

import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.repository.BgmSelection
import com.baidu.tv.player.kt.repository.PlayableUrlResolver
import com.baidu.tv.player.kt.ui.playback.PlaybackUiState
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * BGM 决策矩阵测试（Task 6.2）。
 *
 * 使用 [FakeBackgroundAudioPlayer] 验证协调器在各种播放状态下对播放器的实际调用。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundMusicCoordinatorDecisionTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val resolver: PlayableUrlResolver = mockk()
    private val selection = BgmSelection(fsId = 100L, path = "/music/a.mp3", name = "a.mp3")
    private val headers = mapOf("User-Agent" to "pan.baidu.com")
    private val bgmUrl = "http://a.com/music.mp3"

    private fun imagePlayingState() = PlaybackUiState(
        currentFile = FileInfo(fsId = 1, path = "/photos/a.jpg", serverFilename = "a.jpg", category = 3),
        isPlaying = true,
    )

    private fun imagePausedState() = PlaybackUiState(
        currentFile = FileInfo(fsId = 1, path = "/photos/a.jpg", serverFilename = "a.jpg", category = 3),
        isPlaying = false,
    )

    private fun videoPlayingState() = PlaybackUiState(
        currentFile = FileInfo(fsId = 1, path = "/movies/a.mp4", serverFilename = "a.mp4", category = 1),
        isPlaying = true,
    )

    /** 在 runTest 内调用：创建已就绪的协调器和对应 FakePlayer。 */
    private fun readyCoordinator(): Pair<BackgroundMusicCoordinator, FakeBackgroundAudioPlayer> {
        val player = FakeBackgroundAudioPlayer()
        val coordinator = BackgroundMusicCoordinator(resolver, player)
            .withScope(mainCoroutineRule.scope)
        coEvery { resolver.resolve(100L, null, null) } returns bgmUrl
        coordinator.setSelection(selection, headers)
        mainCoroutineRule.scope.advanceUntilIdle()
        return coordinator to player
    }

    @Test
    fun imagePlaying_foreground_playerPlays() = runTest {
        val (coordinator, player) = readyCoordinator()
        coordinator.onForegroundChanged(true)
        coordinator.onPlaybackStateChanged(imagePlayingState())

        val playCalls = player.calls.filterIsInstance<FakeBackgroundAudioPlayer.Call.Play>()
        assertTrue("expected at least one Play call, calls=${player.calls}", playCalls.isNotEmpty())
        assertTrue(playCalls.last().url == bgmUrl)
    }

    @Test
    fun imagePaused_foreground_playerPauses() = runTest {
        val (coordinator, player) = readyCoordinator()
        coordinator.onForegroundChanged(true)
        coordinator.onPlaybackStateChanged(imagePausedState())

        assertTrue("expected Pause in calls=${player.calls}", player.calls.any { it is FakeBackgroundAudioPlayer.Call.Pause })
    }

    @Test
    fun imagePlaying_background_playerPauses() = runTest {
        val (coordinator, player) = readyCoordinator()
        coordinator.onForegroundChanged(false)
        coordinator.onPlaybackStateChanged(imagePlayingState())

        assertTrue("expected Pause in calls=${player.calls}", player.calls.any { it is FakeBackgroundAudioPlayer.Call.Pause })
    }

    @Test
    fun videoPlaying_foreground_playerPauses() = runTest {
        val (coordinator, player) = readyCoordinator()
        coordinator.onForegroundChanged(true)
        coordinator.onPlaybackStateChanged(videoPlayingState())

        assertTrue("expected Pause in calls=${player.calls}", player.calls.any { it is FakeBackgroundAudioPlayer.Call.Pause })
    }

    @Test
    fun noSelection_playerStops() = runTest {
        val (coordinator, player) = readyCoordinator()
        coordinator.setSelection(null, headers)

        assertTrue("expected Stop in calls=${player.calls}", player.calls.any { it is FakeBackgroundAudioPlayer.Call.Stop })
    }

    @Test
    fun videoToImage_playing_foreground_resumesPlay() = runTest {
        val (coordinator, player) = readyCoordinator()
        coordinator.onForegroundChanged(true)
        // 先视频（暂停 BGM）
        coordinator.onPlaybackStateChanged(videoPlayingState())
        // 再切回图片（恢复 BGM）
        coordinator.onPlaybackStateChanged(imagePlayingState())

        val playCalls = player.calls.filterIsInstance<FakeBackgroundAudioPlayer.Call.Play>()
        assertTrue("expected Play after video→image, calls=${player.calls}", playCalls.isNotEmpty())
        assertTrue(playCalls.last().url == bgmUrl)
    }
}
