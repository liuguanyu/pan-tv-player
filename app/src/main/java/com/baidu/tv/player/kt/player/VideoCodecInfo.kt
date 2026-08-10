package com.baidu.tv.player.kt.player

/**
 * 视频编码参数（由 [VideoCodecInspector] 从媒体元数据中提取）。
 *
 * 对应原 Java 通过 `MediaMetadataRetriever` 读取的 mime / 宽 / 高 / bit-depth，
 * 用于 [PlaybackCapability] 评估是否需要软解降级或弹出分级提示。
 */
data class VideoCodecInfo(
    /** 视频 MIME，如 "video/hevc" / "video/avc"；无法读取时为 null。 */
    val mimeType: String?,
    /** 视频宽（像素），未知为 0。 */
    val width: Int,
    /** 视频高（像素），未知为 0。 */
    val height: Int,
    /**
     * 色深（bit）。8 = 普通 8-bit，10 = 10-bit HEVC（HDR/Main10）。
     * MediaMetadataRetriever 无直接字段，通过 color profile / codec 特征推断；未知为 8。
     */
    val bitDepth: Int = 8,
) {
    /** 归一化后的编码短名（去掉 "video/" 前缀，小写）；无则 null。 */
    val codec: String?
        get() = mimeType?.substringAfterLast('/')?.lowercase()

    /** 是否 HEVC/H.265 编码。 */
    fun isHevc(): Boolean = codec == "hevc"

    /** 是否 Dolby Vision 编码。 */
    fun isDolbyVision(): Boolean = mimeType == "video/dolby-vision"

    /** 是否 H.264/AVC 编码。 */
    fun isAvc(): Boolean = codec == "avc"

    /** 是否 4K 及以上分辨率（长边 >= 3840 或短边 >= 2160）。 */
    fun is4kOrAbove(): Boolean = maxOf(width, height) >= 3840 || minOf(width, height) >= 2160

    /** 是否 10-bit 及以上色深。 */
    fun is10BitOrAbove(): Boolean = bitDepth >= 10

    companion object {
        /** 检测失败时的空信息。 */
        val UNKNOWN = VideoCodecInfo(mimeType = null, width = 0, height = 0, bitDepth = 8)
    }
}
