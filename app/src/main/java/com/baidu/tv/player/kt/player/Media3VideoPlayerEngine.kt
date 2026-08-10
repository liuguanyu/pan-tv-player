package com.baidu.tv.player.kt.player

import android.content.Context
import android.util.Log
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "Media3Engine"
private const val USER_AGENT = "BaiduTVPlayer-KT/1.0 (Android TV)"

/**
 * 基于 Media3 ExoPlayer 的播放引擎实现（对应 tasks 6.3），是 [VideoPlayerEngine] 的唯一实现。
 *
 * 硬解优先 + 软解降级链（design.md Decision 2）：
 * ```
 * Media3 硬解 (MediaCodec) → 失败 → [FFmpeg 软解 预留插入点] → 编码检测预检 → 提示
 * ```
 *
 * ### 软硬解降级判定路径
 * 1. [play] 先调用 [VideoCodecInspector] 检测编码参数（[VideoCodecInfo]）。
 * 2. [PlaybackCapability.evaluate] 分级：
 *    - `DirectPlay` → 交给 ExoPlayer 硬解（[buildRenderersFactory] 已开启 `setEnableDecoderFallback`）。
 *    - `WarnAndPlay(reason)` → 返回 [PlaybackResult.Unsupported]，由 UI 弹分级对话框；用户确认后
 *      调用 [playForced] 强制以硬解尝试（4K/10-bit HEVC 仍走 MediaCodec）。
 *    - `Unsupported(reason)` → 返回 [PlaybackResult.Unsupported]（无硬解 + FFmpeg 未接入）。
 *
 * ### Dolby Vision 预检拦截
 * 部分片源为 Dolby Vision（`video/dolby-vision`，如 `hev1.08.04` = DV Profile 8）。
 * 多数 TV 设备无 DV/10-bit 硬解，MediaCodecVideoRenderer 初始化或渲染时会失败。
 * 当前未集成 FFmpeg 软解，因此 [play] 在检测到 DV 后直接返回 [PlaybackResult.Unsupported]，
 * 由 UI 提示并跳过，避免反复弹出底层 MediaCodec 错误。
 *
 * ### FFmpeg 软解未集成（design.md 实现期发现 2026-07-11）
 * `androidx.media3:media3-ffmpeg-decoder` 无 Maven 预构建产物，暂无法接入真实软解 .so。
 * [buildRenderersFactory] 中 `EXTENSION_RENDERER_MODE_ON` 已预留：一旦引入自编译/可信社区
 * FFmpeg 扩展，Media3 会自动在硬解失败时启用软解 renderer，无需改动本类调用方与降级判定。
 * 见 [FFMPEG_SOFT_DECODE_INSERTION_POINT]。
 */
class Media3VideoPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val codecInspector: VideoCodecInspector,
    private val playbackCapability: PlaybackCapability,
) : VideoPlayerEngine {

    private var exoPlayer: ExoPlayer? = null

    /** 播放状态回调（可选）。由 Activity 注入以驱动 UI（进度/缓冲/结束）。 */
    var listener: Listener? = null

    interface Listener {
        fun onReady(durationMs: Long) {}
        fun onEnded() {}
        fun onIsPlayingChanged(isPlaying: Boolean) {}
        fun onError(error: PlaybackException) {}
        fun onUnsupported(reason: UnsupportedReason) {}

        /**
         * 视频实际显示尺寸（已按像素宽高比 SAR 折算）。
         * 裸 SurfaceView 渲染时解码器会把画面拉伸铺满 Surface，
         * UI 层需据此把 SurfaceView 调整为正确宽高比（fit-center）。
         */
        fun onVideoSizeChanged(width: Int, height: Int) {}
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> listener?.onReady(duration())
                Player.STATE_ENDED -> listener?.onEnded()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listener?.onIsPlayingChanged(isPlaying)
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width <= 0 || videoSize.height <= 0) return
            val displayWidth = (videoSize.width * videoSize.pixelWidthHeightRatio).toInt()
            listener?.onVideoSizeChanged(displayWidth, videoSize.height)
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            if (tracks.groups.any { group -> group.mediaTrackGroup.getFormat(0).isDolbyVisionFormat() }) {
                Log.w(TAG, "检测到 Dolby Vision 轨道，停止播放以避免 MediaCodecVideoRenderer 错误")
                stop()
                listener?.onUnsupported(UnsupportedReason.DOLBY_VISION)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer 错误: ${error.errorCodeName}", error)
            listener?.onError(error)
        }
    }

    override suspend fun play(
        url: String,
        surface: Surface,
        headers: Map<String, String>,
        preferredBackend: VideoPlayerEngine.Backend?,
    ): PlaybackResult {
        val info = codecInspector.inspect(url, headers)
        if (info.isDolbyVision()) {
            return PlaybackResult.Unsupported(info.codec, UnsupportedReason.DOLBY_VISION)
        }
        return when (val capability = playbackCapability.evaluate(info)) {
            is PlaybackCapability.Capability.DirectPlay -> {
                startExo(url, surface, headers)
                PlaybackResult.Success
            }

            is PlaybackCapability.Capability.WarnAndPlay ->
                PlaybackResult.Unsupported(info.codec, capability.reason)

            is PlaybackCapability.Capability.Unsupported ->
                PlaybackResult.Unsupported(info.codec, capability.reason)
        }
    }

    /**
     * 用户在分级对话框确认后，强制以硬解尝试播放（用于 4K / 10-bit HEVC 等 WarnAndPlay 场景）。
     * 跳过能力评估，直接交给 ExoPlayer + decoder fallback。
     */
    suspend fun playForced(
        url: String,
        surface: Surface,
        headers: Map<String, String> = emptyMap(),
    ): PlaybackResult = try {
        startExo(url, surface, headers)
        PlaybackResult.Success
    } catch (e: Exception) {
        PlaybackResult.Error(e)
    }

    private suspend fun startExo(
        url: String,
        surface: Surface,
        headers: Map<String, String>,
    ) = withContext(Dispatchers.Main) {
        startExoInternal(url, surface, headers)
    }

    /** 主线程调用：构建媒体源并启动播放；[startPositionMs] > 0 时从该位置续播（DV 重试场景）。 */
    private fun startExoInternal(
        url: String,
        surface: Surface,
        headers: Map<String, String>,
        startPositionMs: Long = 0L,
    ) {
        val player = ensurePlayer()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource = DefaultMediaSourceFactory(httpFactory).createMediaSource(mediaItem)
        player.setVideoSurface(surface)
        if (startPositionMs > 0) {
            player.setMediaSource(mediaSource, startPositionMs)
        } else {
            player.setMediaSource(mediaSource)
        }
        player.prepare()
        player.playWhenReady = true
    }

    private fun ensurePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 2_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .build()
        return ExoPlayer.Builder(context)
            .setRenderersFactory(buildRenderersFactory())
            .setLoadControl(loadControl)
            .build()
            .also {
                it.addListener(playerListener)
                exoPlayer = it
            }
    }

    /**
     * 构建 RenderersFactory：开启硬解，`EXTENSION_RENDERER_MODE_ON` +
     * `setEnableDecoderFallback(true)` 预留软解降级路径。
     *
     * [FFMPEG_SOFT_DECODE_INSERTION_POINT]：当前无 FFmpeg 扩展，`EXTENSION_RENDERER_MODE_ON`
     * 不会实际加载软解 renderer；接入后 Media3 会自动在硬解失败时启用扩展 renderer。
     */
    private fun buildRenderersFactory(): DefaultRenderersFactory =
        DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

    private fun Format.isDolbyVisionFormat(): Boolean =
        sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION || codecs.isDolbyVisionCodecString()

    private fun String?.isDolbyVisionCodecString(): Boolean {
        val value = this?.lowercase() ?: return false
        return value.startsWith("dvhe") ||
            value.startsWith("dvh1") ||
            value.startsWith("hev1.08") ||
            value.startsWith("hvc1.08")
    }

    override fun switchBackend(backend: VideoPlayerEngine.Backend) = Unit

    override fun resume() {
        exoPlayer?.play()
    }

    override fun pause() {
        exoPlayer?.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    override fun currentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    override fun duration(): Long {
        val d = exoPlayer?.duration ?: 0L
        return if (d == C.TIME_UNSET) 0L else d
    }

    override fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false

    override fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearVideoSurface()
    }

    override fun release() {
        exoPlayer?.let {
            it.removeListener(playerListener)
            it.release()
        }
        exoPlayer = null
    }

    companion object {
        /**
         * FFmpeg 软解接入点标记（design.md Decision 2）。
         * 当前 `media3-ffmpeg-decoder` 无 Maven 产物；接入自编译扩展后，
         * 在 [buildRenderersFactory] 中 `EXTENSION_RENDERER_MODE_ON` 即会启用软解 renderer。
         */
        const val FFMPEG_SOFT_DECODE_INSERTION_POINT = "buildRenderersFactory"
    }
}
