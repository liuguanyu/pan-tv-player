package com.baidu.tv.player.kt.player

/**
 * 播放能力分级评估器（对应 tasks 6.5）。
 *
 * 依据 [VideoCodecInfo] 与设备硬解能力（[HardwareDecoderChecker]）评估该视频的播放策略：
 * - [Capability.DirectPlay]     H.264 或设备可硬解的编码，直接硬解播放。
 * - [Capability.WarnAndPlay]    4K HEVC / 10-bit HEVC，硬解可能不稳定，弹提示后仍可尝试播放。
 * - [Capability.Unsupported]    设备无硬解且无软解兜底（FFmpeg 未接入），提示无法播放。
 *
 * 纯逻辑类（无 Android 依赖），便于单测。UI 层据此弹出分级对话框（见 [PlaybackCapabilityDialog]）。
 */
class PlaybackCapability(
    private val hardwareDecoderChecker: HardwareDecoderChecker,
) {
    /** 评估结果分级。 */
    sealed class Capability {
        /** 直接播放（硬解）。 */
        data object DirectPlay : Capability()

        /** 可播放但需警告（可能卡顿/花屏），携带原因。 */
        data class WarnAndPlay(val reason: UnsupportedReason) : Capability()

        /** 无法播放（无硬解 + 无软解），携带原因。 */
        data class Unsupported(val reason: UnsupportedReason) : Capability()
    }

    /**
     * 评估给定编码信息的播放能力。
     *
     * 判定顺序（与 design.md 分级一致）：
     * 1. Dolby Vision：无 DV/10-bit 能力时 MediaCodec 易直接报错，且无软解兜底 → 不支持。
     * 2. 非 HEVC（含检测失败/H.264/其他），交给硬解直接播放（Media3 自带 decoder fallback）。
     * 3. HEVC 10-bit：无论分辨率，硬解在低端 Amlogic 上易花屏 → 警告。
     * 4. HEVC 4K：解码压力大 → 警告。
     * 5. HEVC 8-bit 1080p 及以下：若设备可硬解则直接播放；否则（无软解兜底）不支持。
     */
    fun evaluate(info: VideoCodecInfo): Capability {
        if (info.isDolbyVision()) {
            return Capability.Unsupported(UnsupportedReason.DOLBY_VISION)
        }
        if (!info.isHevc()) {
            return Capability.DirectPlay
        }
        if (info.is10BitOrAbove()) {
            return Capability.WarnAndPlay(UnsupportedReason.HEVC_10BIT)
        }
        if (info.is4kOrAbove()) {
            return Capability.WarnAndPlay(UnsupportedReason.HEVC_4K)
        }
        // HEVC 8-bit，常规分辨率：依赖设备硬解能力。
        return if (hardwareDecoderChecker.canDecodeHardware(info)) {
            Capability.DirectPlay
        } else {
            // FFmpeg 软解未接入 → 无兜底。
            Capability.Unsupported(UnsupportedReason.NO_HARDWARE_DECODER)
        }
    }
}

/**
 * 设备硬件解码能力检测抽象（便于单测 mock）。
 *
 * 默认实现 [MediaCodecHardwareDecoderChecker] 查询 `MediaCodecList` 判断是否存在
 * 匹配该 MIME 的硬件解码器。
 */
fun interface HardwareDecoderChecker {
    /** 设备是否具备可硬解该 [info] 编码的解码器。 */
    fun canDecodeHardware(info: VideoCodecInfo): Boolean
}
