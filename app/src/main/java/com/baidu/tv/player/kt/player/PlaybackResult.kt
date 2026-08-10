package com.baidu.tv.player.kt.player

/**
 * 播放引擎 [VideoPlayerEngine.play] 的结果密封类。
 *
 * 对应 design.md Decision 2 的“硬解 → 软解 → 编码检测预检”降级链：
 * - [Success]     媒体已成功准备并交由引擎播放（硬解 MediaCodec 或未来 FFmpeg 软解）。
 * - [Unsupported] 检测到当前设备无法（硬解）解码该编码，且软解暂不可用（FFmpeg 未接入）。
 *                 携带 [codec] 供 UI 层弹出分级提示对话框（4K HEVC / 10-bit HEVC 等）。
 * - [Error]       其他播放准备失败（网络、URL 无效、引擎内部异常等）。
 */
sealed class PlaybackResult {

    /** 播放准备成功。 */
    data object Success : PlaybackResult()

    /**
     * 编码不受支持（硬解不可用且软解未接入）。
     *
     * @param codec    检测到的视频编码（如 "hevc" / "avc"），可能为 null（检测失败）。
     * @param reason   不支持的具体分级原因，供 UI 提示与测试断言。
     */
    data class Unsupported(
        val codec: String?,
        val reason: UnsupportedReason,
    ) : PlaybackResult()

    /**
     * 播放准备失败。
     *
     * @param cause 失败原因（异常）。
     */
    data class Error(val cause: Throwable) : PlaybackResult()
}

/**
 * 不受支持的分级原因（对应 6.5 分级对话框）。
 */
enum class UnsupportedReason {
    /** 10-bit HEVC：软解在低端 Amlogic 上卡顿，硬解可能花屏，需用户确认。 */
    HEVC_10BIT,

    /** 4K 分辨率 HEVC：解码压力大，需用户确认是否继续。 */
    HEVC_4K,

    /** Dolby Vision：设备无 DV/10-bit 能力时普通硬解会报 MediaCodecVideoRenderer error。 */
    DOLBY_VISION,

    /** 设备 MediaCodec 明确不支持该编码，且无软解兜底。 */
    NO_HARDWARE_DECODER,
}
