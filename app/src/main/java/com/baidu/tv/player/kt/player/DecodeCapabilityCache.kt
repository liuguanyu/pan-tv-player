package com.baidu.tv.player.kt.player

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备解码能力内存缓存（进程级单例）。
 *
 * DV / 10-bit / 特定 MIME 能否硬解是**设备固有属性**，设备不变结果不变，因此：
 * 1. [MediaCodecList] 硬解查询结果按 MIME 缓存（[cacheHardwareDecode]），
 *    避免每个视频重复枚举解码器。
 * 2. Media3 运行时解码失败（如 DV Profile 8 触发 `MediaCodec Error 0xe`）后按
 *    编码特征 key 记忆（[markMedia3Failed]）；同类格式下次播放直接选 LibVLC 软解，
 *    不再让 Media3 先崩一次。
 *
 * 仅存内存：应用重启后重新学习，天然适应系统升级/固件变更导致的能力变化。
 */
@Singleton
class DecodeCapabilityCache @Inject constructor() {

    /** MIME → 是否存在硬解器（[MediaCodecHardwareDecoderChecker] 查询结果）。 */
    private val hardwareDecodeByMime = ConcurrentHashMap<String, Boolean>()

    /** Media3 硬解链已被证实失败的编码特征 key 集合。 */
    private val media3FailedProfiles = ConcurrentHashMap.newKeySet<String>()

    /** 读取 MIME 硬解查询缓存；未缓存返回 null。 */
    fun hardwareDecodeFor(mime: String): Boolean? = hardwareDecodeByMime[mime.lowercase()]

    /** 写入 MIME 硬解查询结果。 */
    fun cacheHardwareDecode(mime: String, supported: Boolean) {
        hardwareDecodeByMime[mime.lowercase()] = supported
    }

    /** 该编码特征此前是否已在本设备上触发过 Media3 解码失败。 */
    fun isMedia3KnownFailing(info: VideoCodecInfo): Boolean =
        profileKey(info) in media3FailedProfiles

    /** 记录该编码特征在 Media3 硬解链上失败（预检漏判被运行时纠正）。 */
    fun markMedia3Failed(info: VideoCodecInfo) {
        media3FailedProfiles += profileKey(info)
    }

    /**
     * 编码特征 key：MIME + 色深 + 是否 4K。
     * 粒度取「解码器选型」相关维度——同 MIME 同色深同分辨率级别的失败结论可复用。
     */
    private fun profileKey(info: VideoCodecInfo): String =
        "${info.mimeType?.lowercase() ?: "unknown"}|${info.bitDepth}bit|${if (info.is4kOrAbove()) "4k" else "sd"}"
}
