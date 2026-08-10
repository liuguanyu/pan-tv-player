package com.baidu.tv.player.kt.player

/**
 * 播放能力分级评估器（对应 tasks 6.5）。
 *
 * 依据 [VideoCodecInfo] 与设备硬解能力（[HardwareDecoderChecker]）评估该视频的播放策略：
 * - [Capability.DirectPlay]     H.264 或设备声明可硬解的 HEVC/Dolby Vision，直接硬解播放。
 * - [Capability.WarnAndPlay]    保留给需要用户确认的风险格式。
 * - [Capability.Unsupported]    设备没有对应硬件解码器，交由混合引擎的 LibVLC 软解兜底。
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
     * 判定原则：
     * 1. 非 HEVC/Dolby Vision（含检测失败/H.264/其他）交给 Media3 直接播放。
     * 2. HEVC/Dolby Vision 查询设备硬件解码器；存在则优先硬解，不因 4K/10-bit 标签预先强制软解。
     * 3. 没有对应硬解器时返回 Unsupported，由 [HybridVideoPlayerEngine] 直接选择 LibVLC。
     *
     * 电视 SoC 的硬解单元通常能处理 4K Main10，而 CPU 纯软解反而容易失败；运行时硬解异常仍会
     * 由混合引擎自动切换 LibVLC，因此这里应以设备实际声明能力为准。
     */
    fun evaluate(info: VideoCodecInfo): Capability {
        if (!info.isHevc() && !info.isDolbyVision()) return Capability.DirectPlay
        if (hardwareDecoderChecker.canDecodeHardware(info)) return Capability.DirectPlay
        return Capability.Unsupported(
            if (info.isDolbyVision()) UnsupportedReason.DOLBY_VISION else UnsupportedReason.NO_HARDWARE_DECODER,
        )
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
