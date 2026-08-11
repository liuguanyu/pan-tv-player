package com.baidu.tv.player.kt.player

import android.content.Context
import android.net.Uri
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.media3.common.PlaybackException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import javax.inject.Inject

private const val VLC_TAG = "LibVlcEngine"

/**
 * 基于 LibVLC 的 FFmpeg 软解兜底播放引擎。
 *
 * 用于 iPhone Dolby Vision / HEVC 10-bit 等 MediaCodec 硬解失败场景。
 * LibVLC AAR 内置 FFmpeg 解码能力，不依赖 Media3 的未发布 FFmpeg 扩展。
 */
class LibVlcVideoPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoPlayerEngine {

    var listener: Media3VideoPlayerEngine.Listener? = null

    private val libVlcDelegate = lazy {
        // 注意：必须使用可变列表。LibVLC(<init>) 内部会对该 options 列表调用 add(...)
        // 追加默认参数，若传入 listOf(...) 这类不可变列表会抛
        // UnsupportedOperationException 导致 LibVLC 构造失败、软解无法启动。
        LibVLC(
            context,
            arrayListOf(
                "--network-caching=3000",
                "--file-caching=1500",
                // 软解兜底时允许丢弃来不及渲染的帧，避免 32 位 TV CPU 被 4K/Main10 拖垮后彻底卡死。
                "--drop-late-frames",
                "--skip-frames",
                "--avcodec-hw=none",
            ),
        ).apply {
            setUserAgent("BaiduTVPlayer-KT", "BaiduTVPlayer-KT/1.0 (Android TV)")
        }
    }
    private val libVlc: LibVLC by libVlcDelegate

    private var mediaPlayer: MediaPlayer? = null
    private var attachedSurface: Surface? = null
    private var videoView: TextureView? = null
    private var knownDurationMs: Long = 0L
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    /**
     * 是否已经成功开始渲染（收到过 [MediaPlayer.Event.Playing]）。
     *
     * LibVLC 在软解正常出帧后仍可能因非致命原因（如模拟器缺少 GL 扩展、
     * `libvlc window: request N not implemented` 等）抛 [MediaPlayer.Event.EncounteredError]。
     * 只有在从未成功起播时的错误才算致命；已出帧后的错误应忽略，避免误弹 Toast + 跳过。
     */
    private var hasStartedRendering: Boolean = false

    /**
     * 记录渲染窗口尺寸。LibVLC 以裸 [Surface]（无 SurfaceHolder）输出时必须调用
     * `IVLCVout.setWindowSize()`，否则 vout 无法确定尺寸导致黑屏（仅有声音/进度）。
     */
    override fun setVideoSurfaceSize(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        mediaPlayer?.vlcVout?.takeIf { it.areViewsAttached() && width > 0 && height > 0 }
            ?.setWindowSize(width, height)
    }

    /**
     * 绑定渲染 [TextureView]。TextureView 可在 Sony Android 9 上安全旋转，避免
     * 旋转 SurfaceView 后出现“有声音但黑屏”。
     */
    override fun setVideoTextureView(textureView: TextureView) {
        videoView = textureView
    }

    override suspend fun play(
        url: String,
        surface: Surface,
        headers: Map<String, String>,
        preferredBackend: VideoPlayerEngine.Backend?,
    ): PlaybackResult = withContext(Dispatchers.Main) {
        try {
            startVlc(url, surface, headers)
            PlaybackResult.Success
        } catch (e: Exception) {
            Log.e(VLC_TAG, "LibVLC 播放失败: ${e.message}", e)
            PlaybackResult.Error(e)
        }
    }

    private fun startVlc(url: String, surface: Surface, headers: Map<String, String>) {
        stop()
        attachedSurface = surface
        val player = MediaPlayer(libVlc).also { mediaPlayer = it }
        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    hasStartedRendering = true
                    listener?.onIsPlayingChanged(true)
                }
                MediaPlayer.Event.Paused,
                MediaPlayer.Event.Stopped -> listener?.onIsPlayingChanged(false)
                MediaPlayer.Event.EndReached -> listener?.onEnded()
                MediaPlayer.Event.EncounteredError -> {
                    if (hasStartedRendering) {
                        // 已成功出帧后的错误多为模拟器 GL/window 非致命告警，忽略以免误跳过。
                        Log.w(VLC_TAG, "LibVLC 出帧后遇到非致命错误，忽略（播放继续）")
                    } else {
                        listener?.onError(
                            PlaybackException(
                                "LibVLC 播放错误",
                                null,
                                PlaybackException.ERROR_CODE_DECODING_FAILED,
                            ),
                        )
                    }
                }
                MediaPlayer.Event.LengthChanged -> {
                    knownDurationMs = event.lengthChanged.coerceAtLeast(0L)
                    listener?.onReady(knownDurationMs)
                }
                MediaPlayer.Event.TimeChanged -> {
                    if (knownDurationMs <= 0L) {
                        knownDurationMs = player.length.coerceAtLeast(0L)
                        if (knownDurationMs > 0L) listener?.onReady(knownDurationMs)
                    }
                }
            }
        }
        val view = videoView
        if (view != null) {
            // 交给 VLC 管理 TextureView 的 SurfaceTexture 生命周期与输出合成。
            player.vlcVout.setVideoView(view)
        } else {
            player.vlcVout.setVideoSurface(surface, null)
        }
        val (windowWidth, windowHeight) = resolveWindowSize()
        player.vlcVout.setWindowSize(windowWidth, windowHeight)
        // onNewVideoLayout：拿到真实视频尺寸与像素宽高比（SAR），上报给 UI 层
        // 调整 TextureView 为正确比例，避免输出被拉伸导致比例失真。
        player.vlcVout.attachViews { _, width, height, visibleWidth, visibleHeight, sarNum, sarDen ->
            val vw = if (visibleWidth > 0) visibleWidth else width
            val vh = if (visibleHeight > 0) visibleHeight else height
            if (vw > 0 && vh > 0) {
                val sar = if (sarNum > 0 && sarDen > 0) sarNum.toDouble() / sarDen else 1.0
                listener?.onVideoSizeChanged((vw * sar).toInt(), vh)
            }
        }
        // scale=0 表示自适应窗口（保持比例缩放到 setWindowSize 指定的区域）。
        player.aspectRatio = null
        player.scale = 0f
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(false, false)
            addOption(":codec=avcodec")
            addOption(":avcodec-hw=none")
            addOption(":network-caching=3000")
            headers.forEach { (key, value) -> addOption(":http-header=$key: $value") }
        }
        player.media = media
        media.release()
        player.play()
    }

    /** 未显式传入尺寸时退化为屏幕分辨率（TV 全屏播放场景）。 */
    private fun resolveWindowSize(): Pair<Int, Int> {
        if (surfaceWidth > 0 && surfaceHeight > 0) return surfaceWidth to surfaceHeight
        val metrics: DisplayMetrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    override fun switchBackend(backend: VideoPlayerEngine.Backend) = Unit

    override fun currentBackend(): VideoPlayerEngine.Backend = VideoPlayerEngine.Backend.LIBVLC

    override fun resume() {
        mediaPlayer?.play()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.time = positionMs.coerceAtLeast(0L)
    }

    override fun currentPosition(): Long = mediaPlayer?.time?.coerceAtLeast(0L) ?: 0L

    override fun duration(): Long = mediaPlayer?.length?.takeIf { it > 0 } ?: knownDurationMs

    override fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    override fun stop() {
        mediaPlayer?.let { player ->
            // 先摘监听，避免 stop 触发的 Stopped 事件回调到已销毁的 UI。
            runCatching { player.setEventListener(null) }
            runCatching { player.stop() }
            runCatching { player.vlcVout.detachViews() }
            runCatching { player.release() }
        }
        mediaPlayer = null
        attachedSurface = null
        knownDurationMs = 0L
        hasStartedRendering = false
    }

    override fun release() {
        stop()
        if (libVlcDelegate.isInitialized()) {
            libVlc.release()
        }
    }
}
