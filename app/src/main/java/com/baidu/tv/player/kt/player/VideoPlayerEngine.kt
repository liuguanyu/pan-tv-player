package com.baidu.tv.player.kt.player

import android.view.Surface

/**
 * 播放引擎抽象接口（对应 tasks 6.1）。
 *
 * 统一 Media3（当前唯一实现 [Media3VideoPlayerEngine]）的播放生命周期，
 * 隔离具体解码栈，便于未来接入 FFmpeg 软解或替换实现，同时便于单测。
 *
 * 线程约定：所有方法在主线程调用（Media3 ExoPlayer 要求主线程访问）。
 * [play] 为 `suspend`，内部完成编码检测（可能阻塞的 [VideoCodecInspector]）后再准备播放。
 */
interface VideoPlayerEngine {

    /** 当前播放栈：硬解 Media3 或 LibVLC/FFmpeg 软解兜底。 */
    enum class Backend {
        MEDIA3,
        LIBVLC,
    }

    /**
     * 准备并开始播放 [url]，渲染到 [surface]。
     *
     * 流程：编码参数检测（[VideoCodecInspector]）→ [PlaybackCapability] 评估 →
     * 硬解直接播放 / 需软解但未接入则返回 [PlaybackResult.Unsupported]。
     *
     * @param url     媒体地址（已拼接 access_token）。
     * @param surface 渲染目标 Surface。
     * @param headers 附加 HTTP 头（如 User-Agent）。
     * @param preferredBackend 播放栈偏好；为 null 时由引擎按编码能力自动选择。
     * @return 播放准备结果。
     */
    suspend fun play(
        url: String,
        surface: Surface,
        headers: Map<String, String> = emptyMap(),
        preferredBackend: Backend? = null,
    ): PlaybackResult

    /**
     * 通知引擎渲染窗口（Surface）的像素尺寸。
     *
     * LibVLC 以裸 Surface 输出时必须知道窗口大小才能渲染（否则黑屏）；
     * Media3 不需要，默认实现为空操作。
     */
    fun setVideoSurfaceSize(width: Int, height: Int) {}

    /**
     * 提供渲染目标 [android.view.SurfaceView]（可选）。
     *
     * LibVLC 用 `IVLCVout.setVideoView(SurfaceView)`，Media3 用 `setVideoSurfaceView(SurfaceView)`；
     * 两者都由播放器跟踪 SurfaceHolder 的尺寸变化与重建，比一次性绑定裸 Surface 更可靠。
     */
    fun setVideoSurfaceView(surfaceView: android.view.SurfaceView) {}

    /** 恢复播放。 */
    fun resume()

    /** 切换当前播放使用的输出视图。 */
    fun switchBackend(backend: Backend)

    /** 当前实际使用的播放栈（播放页信息面板/解码器徽标展示用）。单栈实现返回固定值即可。 */
    fun currentBackend(): Backend = Backend.MEDIA3

    /** 暂停播放。 */
    fun pause()

    /** 跳转到指定位置（毫秒）。 */
    fun seekTo(positionMs: Long)

    /** 当前播放位置（毫秒），未就绪返回 0。 */
    fun currentPosition(): Long

    /** 媒体总时长（毫秒），未知返回 0。 */
    fun duration(): Long

    /** 是否正在播放。 */
    fun isPlaying(): Boolean

    /** 停止播放（保留引擎实例，可再次 [play]）。 */
    fun stop()

    /** 释放引擎与底层资源，之后不可再用。 */
    fun release()
}

/**
 * 视频编码参数检测抽象（对应 tasks 6.4，便于单测 mock）。
 *
 * 默认实现 [MediaMetadataRetrieverCodecInspector] 使用 `MediaMetadataRetriever`，
 * 加 `withTimeout` 保护，避免对百度网盘 CDN 重定向链无限阻塞（design.md 痛点 #4）。
 */
fun interface VideoCodecInspector {
    /**
     * 检测 [url] 的视频编码参数。失败或超时返回 [VideoCodecInfo.UNKNOWN]。
     *
     * @param headers 附加 HTTP 头。
     */
    suspend fun inspect(url: String, headers: Map<String, String>): VideoCodecInfo
}
