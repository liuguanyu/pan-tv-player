package com.baidu.tv.player.kt.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * BGM 目标动作决策矩阵测试（Task 1.3）。
 *
 * 此测试定义了 BackgroundMusicCoordinator 应实现的目标行为真值表，
 * 覆盖媒体类型、用户播放意图、前台生命周期和配置组合。
 *
 * Phase 2 将修复 Activity 的 syncBackgroundMusic 以匹配此矩阵；
 * Phase 5/6 将提取真正的 Coordinator 并用此测试验证。
 *
 * 当前 Activity 代码的已知偏差：
 * - syncBackgroundMusic 只检查 isCurrentImage && !bgmPausedByLifecycle，未检查 isPlaying
 *   → 图片暂停时 BGM 仍在播放（Phase 2.1–2.2 修复）
 * - bgmSelectionKey 在解析成功前就设置，解析失败后无法重试
 *   → BGM 解析失败不可恢复（Phase 2.6 修复）
 * - runCatching 会吞掉 CancellationException（Phase 2.6 修复）
 */
class BgmDecisionMatrixTest {

    /**
     * 纯 Kotlin 函数：根据当前状态计算 BGM 目标动作。
     * 这是指引后续重构的规范（spec），不是当前生产代码。
     */
    private fun computeBgmAction(
        hasSelection: Boolean,
        isResolved: Boolean,
        isCurrentImage: Boolean,
        isCurrentVideo: Boolean,
        isPlaying: Boolean,
        isForeground: Boolean,
    ): BgmTargetAction {
        if (!hasSelection) return BgmTargetAction.STOP
        if (!isResolved) return BgmTargetAction.NONE
        if (isCurrentVideo) return BgmTargetAction.PAUSE
        // 图片场景：用户播放且前台 → Play；其他 → Pause
        if (isCurrentImage) {
            return if (isPlaying && isForeground) BgmTargetAction.PLAY else BgmTargetAction.PAUSE
        }
        // 非图片非视频（不应发生）：不操作
        return BgmTargetAction.NONE
    }

    // ---- 无选择 ----

    @Test
    fun noSelection_alwaysStops() {
        assertEquals(
            BgmTargetAction.STOP,
            computeBgmAction(
                hasSelection = false, isResolved = false,
                isCurrentImage = true, isCurrentVideo = false,
                isPlaying = true, isForeground = true,
            ),
        )
    }

    // ---- 解析中 ----

    @Test
    fun resolving_doesNotTouchPlayer() {
        assertEquals(
            BgmTargetAction.NONE,
            computeBgmAction(
                hasSelection = true, isResolved = false,
                isCurrentImage = true, isCurrentVideo = false,
                isPlaying = true, isForeground = true,
            ),
        )
    }

    // ---- 图片 + 播放中 + 前台 → Play ----

    @Test
    fun imagePlaying_foreground_resolved_plays() {
        assertEquals(
            BgmTargetAction.PLAY,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = true, isCurrentVideo = false,
                isPlaying = true, isForeground = true,
            ),
        )
    }

    // ---- 图片 + 用户暂停 → Pause ----

    @Test
    fun imageUserPaused_foreground_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = true, isCurrentVideo = false,
                isPlaying = false, isForeground = true,
            ),
        )
    }

    // ---- 图片 + 后台 → Pause ----

    @Test
    fun imagePlaying_background_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = true, isCurrentVideo = false,
                isPlaying = true, isForeground = false,
            ),
        )
    }

    @Test
    fun imageUserPaused_background_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = true, isCurrentVideo = false,
                isPlaying = false, isForeground = false,
            ),
        )
    }

    // ---- 视频 → Pause（不论播放状态和生命周期） ----

    @Test
    fun videoPlaying_foreground_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = false, isCurrentVideo = true,
                isPlaying = true, isForeground = true,
            ),
        )
    }

    @Test
    fun videoPaused_foreground_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = false, isCurrentVideo = true,
                isPlaying = false, isForeground = true,
            ),
        )
    }

    @Test
    fun video_background_resolved_pauses() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = false, isCurrentVideo = true,
                isPlaying = true, isForeground = false,
            ),
        )
    }

    // ---- 生命周期不覆盖用户暂停意图 ----

    @Test
    fun userPaused_returningToForeground_staysPaused() {
        assertEquals(
            BgmTargetAction.PAUSE,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = true, isCurrentVideo = false,
                isPlaying = false, isForeground = true,
            ),
        )
    }

    // ---- 切回图片时从现有位置恢复 ----

    @Test
    fun videoToImage_playing_foreground_resumes() {
        assertEquals(
            BgmTargetAction.PLAY,
            computeBgmAction(
                hasSelection = true, isResolved = true,
                isCurrentImage = true, isCurrentVideo = false,
                isPlaying = true, isForeground = true,
            ),
        )
    }

    /** BGM 目标动作枚举。 */
    private enum class BgmTargetAction { PLAY, PAUSE, STOP, NONE }
}
