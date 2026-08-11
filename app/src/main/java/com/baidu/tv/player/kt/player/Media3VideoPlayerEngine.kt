package com.baidu.tv.player.kt.player

import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
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
 * 基于 Media3 ExoPlayer 的硬件优先播放引擎（对应 tasks 6.3）。
 *
 * [play] 通过 [VideoCodecInspector] 和 [PlaybackCapability] 判断设备是否声明了目标编码硬解器；
 * 可硬解时直接播放，包括设备支持的 4K/Main10/Dolby Vision。Media3 启动或运行时失败后，
 * 上层 [HybridVideoPlayerEngine] 负责切换到 LibVLC/FFmpeg 软解。
 *
 * [buildRenderersFactory] 同时开启 Media3 decoder fallback；扩展 renderer 插入点保留在
 * [FFMPEG_SOFT_DECODE_INSERTION_POINT]，当前真正的通用软解兜底由 LibVLC 提供。
 */
class Media3VideoPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val codecInspector: VideoCodecInspector,
    private val playbackCapability: PlaybackCapability,
) : VideoPlayerEngine {

    private var exoPlayer: ExoPlayer? = null
    private var videoTextureView: TextureView? = null

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
         * TextureView 渲染时画面会铺满输出 View，UI 层需据此调整为正确宽高比
         * （fit-center）。
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
            // API 21+ 旋转由 Media3/MediaCodec 内部应用，VideoSize 已是实际显示方向；
            // 此处只应用像素宽高比（SAR）；设备输出补偿由 UI 的 TextureView 统一处理。
            listener?.onVideoSizeChanged(displayWidth, videoSize.height)
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

    /** 跳过能力预检，直接交给 ExoPlayer + decoder fallback。混合引擎已完成统一评估时使用。 */
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
        val textureView = videoTextureView
        if (textureView != null) {
            // TextureView 支持可靠的旋转/缩放变换；Sony Android 9 上避免旋转
            // SurfaceView 导致“有声音但黑屏”。
            player.setVideoTextureView(textureView)
        } else {
            player.setVideoSurface(surface)
        }
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


    override fun switchBackend(backend: VideoPlayerEngine.Backend) = Unit

    override fun setVideoTextureView(textureView: TextureView) {
        videoTextureView = textureView
    }

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
