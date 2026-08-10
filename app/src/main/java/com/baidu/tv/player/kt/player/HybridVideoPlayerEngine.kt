package com.baidu.tv.player.kt.player

import android.util.Log
import android.view.Surface
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HybridEngine"

/**
 * 混合播放引擎：优先使用设备 MediaCodec 硬解，失败时自动切换到 LibVLC/FFmpeg 软解兜底。
 *
 * 兜底路径（三层）：
 * 1. **播放前预检**：设备没有对应硬解器，或同类编码此前已失败 → 直接选 LibVLC。
 * 2. **准备阶段**：Media3 同步启动失败或返回 [PlaybackResult.Unsupported] → 用 LibVLC 重试。
 * 3. **运行时**：MediaCodec 解码、音轨或容器解析失败 → 从当前进度切换 LibVLC；网络与鉴权
 *    错误不切换，保留原始错误供 UI 提示。
 */
class HybridVideoPlayerEngine @Inject constructor(
    private val media3Engine: Media3VideoPlayerEngine,
    private val libVlcEngine: LibVlcVideoPlayerEngine,
    private val codecInspector: VideoCodecInspector,
    private val playbackCapability: PlaybackCapability,
    private val capabilityCache: DecodeCapabilityCache,
) : VideoPlayerEngine {

    var listener: Media3VideoPlayerEngine.Listener? = null
        set(value) {
            field = value
            libVlcEngine.listener = value
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var activeBackend = VideoPlayerEngine.Backend.MEDIA3

    /** 最近一次 [play] 的参数，用于运行时降级重试。 */
    private var lastUrl: String? = null
    private var lastHeaders: Map<String, String> = emptyMap()
    private var lastSurface: Surface? = null

    /** 最近一次预检得到的编码信息，用于运行时失败后写入 [DecodeCapabilityCache]。 */
    private var lastCodecInfo: VideoCodecInfo = VideoCodecInfo.UNKNOWN

    /** 每个媒体只允许一次自动降级，避免 LibVLC 也失败时无限循环。 */
    private var failoverAttempted = false

    /**
     * Media3 内部监听包装：解码类错误 / 不支持回调触发 LibVLC 自动降级，其余透传给外部 [listener]。
     */
    private val media3ListenerProxy = object : Media3VideoPlayerEngine.Listener {
        override fun onReady(durationMs: Long) {
            listener?.onReady(durationMs)
        }

        override fun onEnded() {
            listener?.onEnded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            listener?.onIsPlayingChanged(isPlaying)
        }

        override fun onVideoSizeChanged(width: Int, height: Int) {
            listener?.onVideoSizeChanged(width, height)
        }

        override fun onError(error: PlaybackException) {
            if (activeBackend == VideoPlayerEngine.Backend.MEDIA3 && shouldFailover(error)) {
                Log.w(
                    TAG,
                    "Media3 播放失败（${error.errorCodeName}: ${error.message.orEmpty()}），自动降级 LibVLC",
                )
                failoverToLibVlc { listener?.onError(error) }
            } else {
                listener?.onError(error)
            }
        }

        override fun onUnsupported(reason: UnsupportedReason) {
            if (activeBackend == VideoPlayerEngine.Backend.MEDIA3) {
                Log.w(TAG, "Media3 不支持（$reason），自动降级 LibVLC 软解")
                failoverToLibVlc { listener?.onUnsupported(reason) }
            } else {
                listener?.onUnsupported(reason)
            }
        }
    }

    init {
        media3Engine.listener = media3ListenerProxy
    }

    override suspend fun play(
        url: String,
        surface: Surface,
        headers: Map<String, String>,
        preferredBackend: VideoPlayerEngine.Backend?,
    ): PlaybackResult {
        lastUrl = url
        lastHeaders = headers
        lastSurface = surface
        failoverAttempted = false
        val info = codecInspector.inspect(url, headers)
        lastCodecInfo = info
        val backend = preferredBackend ?: selectBackend(info)
        return playWithBackend(backend, url, surface, headers)
    }

    private fun selectBackend(info: VideoCodecInfo): VideoPlayerEngine.Backend =
        if (capabilityCache.isMedia3KnownFailing(info)) {
            // 同类编码此前已在本设备上让 Media3 失败过（设备属性不变），直接软解。
            Log.i(TAG, "命中解码失败缓存（${info.mimeType}/${info.bitDepth}bit），直接使用 LibVLC")
            VideoPlayerEngine.Backend.LIBVLC
        } else {
            when (playbackCapability.evaluate(info)) {
                is PlaybackCapability.Capability.DirectPlay -> VideoPlayerEngine.Backend.MEDIA3
                is PlaybackCapability.Capability.WarnAndPlay -> VideoPlayerEngine.Backend.LIBVLC
                is PlaybackCapability.Capability.Unsupported -> VideoPlayerEngine.Backend.LIBVLC
            }
        }

    private suspend fun playWithBackend(
        backend: VideoPlayerEngine.Backend,
        url: String,
        surface: Surface,
        headers: Map<String, String>,
    ): PlaybackResult {
        switchBackend(backend)
        return when (backend) {
            VideoPlayerEngine.Backend.MEDIA3 -> {
                // 用 playForced 跳过 Media3 内部重复的编码检测（Hybrid 已 inspect + 评估过，
                // 避免对同一 URL 连续两次 8s 超时探测导致起播极慢）。
                when (val result = media3Engine.playForced(url, surface, headers)) {
                    PlaybackResult.Success -> result
                    is PlaybackResult.Unsupported -> {
                        Log.w(TAG, "Media3 拒绝播放（${result.reason}），改用 LibVLC 软解")
                        capabilityCache.markMedia3Failed(lastCodecInfo)
                        failoverAttempted = true
                        playWithBackend(VideoPlayerEngine.Backend.LIBVLC, url, surface, headers)
                    }
                    is PlaybackResult.Error -> {
                        Log.w(TAG, "Media3 启动失败（${result.cause.message}），改用 LibVLC")
                        capabilityCache.markMedia3Failed(lastCodecInfo)
                        failoverAttempted = true
                        playWithBackend(VideoPlayerEngine.Backend.LIBVLC, url, surface, headers)
                    }
                }
            }

            VideoPlayerEngine.Backend.LIBVLC -> libVlcEngine.play(url, surface, headers, backend)
        }
    }

    /**
     * 运行时降级：停止 Media3，用 LibVLC 从当前进度续播。
     *
     * - 已降级且 LibVLC 已接手：说明这是 Media3 迟到的错误回调（onError 与 onUnsupported
     *   可能对同一次故障各触发一次），**静默忽略**，绝不能透传给 UI（否则会 toast + 跳过
     *   正在软解播放的视频）。
     * - 缺少重放参数 / LibVLC 自身失败：执行 [onGiveUp] 把原始错误透传给 UI。
     */
    private fun failoverToLibVlc(onGiveUp: () -> Unit) {
        val url = lastUrl
        val surface = lastSurface
        if (failoverAttempted) {
            Log.i(TAG, "忽略 Media3 迟到的错误回调（LibVLC 已接手）")
            return
        }
        if (url == null || surface == null || !surface.isValid) {
            onGiveUp()
            return
        }
        failoverAttempted = true
        // 记忆本设备对该编码特征的 Media3 失败，后续同类视频直接选 LibVLC。
        capabilityCache.markMedia3Failed(lastCodecInfo)
        val resumePositionMs = media3Engine.currentPosition().coerceAtLeast(0L)
        scope.launch {
            // 用 release 而非 stop：错误态 MediaCodec 需彻底释放才会断开 Surface 的
            // BufferQueue producer，否则 LibVLC 无法连接该 Surface（黑屏，仅有声音/进度）。
            runCatching { media3Engine.release() }
                .onFailure { Log.w(TAG, "释放 Media3 失败（可忽略）: ${it.message}") }
            activeBackend = VideoPlayerEngine.Backend.LIBVLC
            when (val result = libVlcEngine.play(url, surface, lastHeaders)) {
                PlaybackResult.Success -> {
                    if (resumePositionMs > 0L) libVlcEngine.seekTo(resumePositionMs)
                }

                is PlaybackResult.Error -> {
                    Log.e(TAG, "LibVLC 兜底播放失败: ${result.cause.message}")
                    onGiveUp()
                }

                is PlaybackResult.Unsupported -> onGiveUp()
            }
        }
    }

    /** 是否为 LibVLC 可兜底的本地播放栈错误；网络、超时和鉴权错误不重复请求。 */
    private fun shouldFailover(error: PlaybackException): Boolean {
        when (error.errorCode) {
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            -> return true
        }
        // errorCode 不在白名单时兜底检查 cause 链：MediaCodec 相关异常一律视为解码错误
        //（如 DV Profile 8 在无硬解设备上抛出的 MediaCodecVideoDecoderException /
        // MediaCodec$CodecException，其 errorCode 因触发路径不同可能各异）。
        var cause: Throwable? = error.cause
        while (cause != null) {
            val name = cause.javaClass.name
            if (name.contains("MediaCodec", ignoreCase = true) ||
                cause is android.media.MediaCodec.CodecException
            ) {
                return true
            }
            cause = cause.cause
        }
        // Media3 的视频/音频 renderer 都可能因设备 MediaCodec 实现缺陷而失败。
        // Sony Android 9 上已观察到 AAC（audio/mp4a-latm）触发 MediaCodecAudioRenderer error；
        // 此类错误同样可由 LibVLC/FFmpeg 软解恢复，不能透传给 UI 后直接跳过视频。
        val message = error.message.orEmpty()
        return message.contains("MediaCodecVideoRenderer error", ignoreCase = true) ||
            message.contains("MediaCodecAudioRenderer error", ignoreCase = true) ||
            message.contains("UnrecognizedInputFormat", ignoreCase = true) ||
            message.contains("ParserException", ignoreCase = true)
    }

    override fun currentBackend(): VideoPlayerEngine.Backend = activeBackend

    override fun switchBackend(backend: VideoPlayerEngine.Backend) {
        if (activeBackend == backend) return
        when (activeBackend) {
            VideoPlayerEngine.Backend.MEDIA3 -> media3Engine.stop()
            VideoPlayerEngine.Backend.LIBVLC -> libVlcEngine.stop()
        }
        activeBackend = backend
    }

    override fun setVideoSurfaceSize(width: Int, height: Int) {
        // 两个引擎都记录，保证运行时降级切换后 LibVLC 也拿到正确窗口尺寸。
        media3Engine.setVideoSurfaceSize(width, height)
        libVlcEngine.setVideoSurfaceSize(width, height)
    }

    override fun setVideoSurfaceView(surfaceView: android.view.SurfaceView) {
        // 两个引擎都绑定同一个 SurfaceView，切换后仍能跟踪 SurfaceHolder 尺寸与生命周期。
        media3Engine.setVideoSurfaceView(surfaceView)
        libVlcEngine.setVideoSurfaceView(surfaceView)
    }

    override fun resume() = activeEngine().resume()

    override fun pause() = activeEngine().pause()

    override fun seekTo(positionMs: Long) = activeEngine().seekTo(positionMs)

    override fun currentPosition(): Long = activeEngine().currentPosition()

    override fun duration(): Long = activeEngine().duration()

    override fun isPlaying(): Boolean = activeEngine().isPlaying()

    override fun stop() = activeEngine().stop()

    override fun release() {
        media3Engine.release()
        libVlcEngine.release()
    }

    private fun activeEngine(): VideoPlayerEngine = when (activeBackend) {
        VideoPlayerEngine.Backend.MEDIA3 -> media3Engine
        VideoPlayerEngine.Backend.LIBVLC -> libVlcEngine
    }
}
