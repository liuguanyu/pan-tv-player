package com.baidu.tv.player.kt.player

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log
import javax.inject.Inject

private const val TAG = "HwDecoderChecker"

/**
 * [HardwareDecoderChecker] 默认实现。
 *
 * 通过 [MediaCodecList] 枚举设备解码器，判断是否存在能解码目标 MIME 的**硬件**解码器
 * （排除以 "OMX.google." / "c2.android." 开头的软件解码器）。
 *
 * 硬解能力是设备固有属性，查询结果经 [DecodeCapabilityCache] 按 MIME 缓存，
 * 同一进程内不重复枚举 [MediaCodecList]。
 *
 * 无法确定编码（mime 为 null）时保守返回 true，交给 Media3 的 decoder fallback 处理，
 * 避免误判导致本可播放的视频被拦截。
 */
class MediaCodecHardwareDecoderChecker @Inject constructor(
    private val capabilityCache: DecodeCapabilityCache,
) : HardwareDecoderChecker {

    override fun canDecodeHardware(info: VideoCodecInfo): Boolean {
        val mime = info.mimeType ?: return true
        capabilityCache.hardwareDecodeFor(mime)?.let { return it }
        return try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val supported = list.codecInfos.any { codec ->
                !codec.isEncoder &&
                    isHardwareDecoder(codec) &&
                    codec.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            capabilityCache.cacheHardwareDecode(mime, supported)
            supported
        } catch (e: Exception) {
            Log.e(TAG, "查询硬解能力失败: ${e.message}")
            // 查询失败时保守放行，交给 Media3 fallback；不缓存失败结果。
            true
        }
    }

    private fun isHardwareDecoder(codec: MediaCodecInfo): Boolean {
        val name = codec.name.lowercase()
        // 软件解码器命名约定：OMX.google.* / c2.android.*
        val isSoftware = name.startsWith("omx.google.") || name.startsWith("c2.android.")
        return !isSoftware
    }
}
