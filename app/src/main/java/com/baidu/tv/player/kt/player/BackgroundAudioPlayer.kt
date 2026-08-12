package com.baidu.tv.player.kt.player

/**
 * 抽象背景音频播放器接口（Phase 5）。
 *
 * 解耦 [PlaybackActivity] 与具体 Media3 实现，使 BGM 播放可注入、可测试。
 * 语义约束：
 * - 相同 URL 再次 [play] 不重新 prepare，仅恢复播放。
 * - 不同 URL [play] 替换 source 并 prepare。
 * - [release] 幂等，可安全多次调用。
 */
interface BackgroundAudioPlayer {
    /** 播放指定 URL 的背景音频；相同 URL 不重新 prepare。 */
    fun play(url: String, headers: Map<String, String>)

    /** 暂停播放。 */
    fun pause()

    /** 停止并清除当前媒体源。 */
    fun stop()

    /** 释放底层资源，幂等。 */
    fun release()
}
