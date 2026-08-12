package com.baidu.tv.player.kt.player

import com.baidu.tv.player.kt.ui.playback.PlaybackUiState

/**
 * 纯 Kotlin BGM 动作决策函数。
 *
 * 根据 BGM 选择、解析状态、当前媒体类型、用户播放意图和前台生命周期，
 * 决定背景音频播放器的目标动作。由 [com.baidu.tv.player.kt.ui.playback.PlaybackActivity]
 * 的 syncBackgroundMusic 调用，确保决策逻辑可独立测试。
 *
 * 约束：
 * - 无选择 → STOP
 * - 解析未完成 → NONE（不触碰播放器）
 * - 当前为视频 → PAUSE（视频期间暂停 BGM，返回图片时恢复）
 * - 当前为图片 + 用户播放中 + 前台 → PLAY
 * - 当前为图片 + 用户暂停或后台 → PAUSE
 */
fun computeBgmAction(
    state: PlaybackUiState,
    hasBgmSelection: Boolean,
    isBgmResolved: Boolean,
    isForeground: Boolean,
): BgmTargetAction {
    if (!hasBgmSelection) return BgmTargetAction.STOP
    if (!isBgmResolved) return BgmTargetAction.NONE
    if (state.isCurrentVideo) return BgmTargetAction.PAUSE
    if (state.isCurrentImage) {
        return if (state.isPlaying && isForeground) BgmTargetAction.PLAY else BgmTargetAction.PAUSE
    }
    return BgmTargetAction.NONE
}

/** BGM 目标动作。 */
enum class BgmTargetAction { PLAY, PAUSE, STOP, NONE }
