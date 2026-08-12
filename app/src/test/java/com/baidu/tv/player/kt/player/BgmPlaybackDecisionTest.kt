package com.baidu.tv.player.kt.player

import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.ui.playback.PlaybackUiState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BGM 决策矩阵测试（Tasks 2.1–2.2）。
 *
 * 验证 [computeBgmAction] 的目标行为：图片暂停时同步暂停 BGM、
 * 暂停状态返回前台不恢复、视频期间暂停 BGM。
 */
class BgmPlaybackDecisionTest {

    // ---- 无选择 → STOP ----

    @Test
    fun noSelection_alwaysStops() {
        assertEquals(
            BgmTargetAction.STOP,
            computeBgmAction(
                state = imagePlayingState(),
                hasBgmSelection = false, isBgmResolved = false, isForeground = true,
            ),
        )
    }

    // ---- 解析中 → NONE ----

    @Test
    fun resolving_doesNotTouchPlayer() {
        assertEquals(
            BgmTargetAction.NONE,
            computeBgmAction(
                state = imagePlayingState(),
                hasBgmSelection = true, isBgmResolved = false, isForeground = true,
            ),
        )
    }

    // ---- 图片 + 播放中 + 前台 → PLAY ----

    @Test
    fun imagePlaying_foreground_resolved_plays() {
        assertEquals(
            BgmTargetAction.PLAY,
            computeBgmAction(
                state = imagePlayingState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = true,
            ),
        )
    }

    // ---- 图片 + 用户暂停 → PAUSE（2.1 核心修复） ----

    @Test
    fun imageUserPaused_foreground_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                state = imagePausedState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = true,
            ),
        )
    }

    // ---- 图片 + 后台 → PAUSE ----

    @Test
    fun imagePlaying_background_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                state = imagePlayingState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = false,
            ),
        )
    }

    @Test
    fun imageUserPaused_background_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                state = imagePausedState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = false,
            ),
        )
    }

    // ---- 视频 → PAUSE ----

    @Test
    fun videoPlaying_foreground_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                state = videoPlayingState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = true,
            ),
        )
    }

    @Test
    fun videoPaused_foreground_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                state = videoPausedState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = true,
            ),
        )
    }

    @Test
    fun video_background_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                state = videoPlayingState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = false,
            ),
        )
    }

    // ---- 生命周期不覆盖用户暂停意图 ----

    @Test
    fun userPaused_returningToForeground_staysPaused() {
        // 用户暂停图片 → onPause（bgmPausedByLifecycle=true）→ onResume（isForeground=true）
        // isPlaying 仍为 false → BGM 保持暂停
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                state = imagePausedState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = true,
            ),
        )
    }

    // ---- 切回图片时从现有位置恢复 ----

    @Test
    fun videoToImage_playing_foreground_resumes() {
        assertEquals(
            BgmTargetAction.PLAY,
            computeBgmAction(
                state = imagePlayingState(),
                hasBgmSelection = true, isBgmResolved = true, isForeground = true,
            ),
        )
    }

    // ---- 辅助构造 ----

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

    private fun videoPausedState() = PlaybackUiState(
        currentFile = FileInfo(fsId = 1, path = "/movies/a.mp4", serverFilename = "a.mp4", category = 1),
        isPlaying = false,
    )
}
