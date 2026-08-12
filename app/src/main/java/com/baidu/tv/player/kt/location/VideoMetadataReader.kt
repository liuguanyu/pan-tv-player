package com.baidu.tv.player.kt.location

import android.media.MediaMetadataRetriever
import javax.inject.Inject

/**
 * [MediaMetadataRetriever] 的可注入封装，便于单元测试对视频 GPS 提取打桩。
 *
 * 仅读取媒体自带的 metadata/location，不获取设备定位（见 design.md Decision 11）。
 */
interface VideoMetadataReader {
    /**
     * 从远程视频 URL 读取 ISO6709 位置字符串（[MediaMetadataRetriever.METADATA_KEY_LOCATION]）。
     * 无 metadata 返回 null。异常由调用方处理（此处允许抛出，由上层静默捕获）。
     */
    fun readLocationString(url: String): String?

    /**
     * 从远程视频 URL 读取拍摄日期字符串（[MediaMetadataRetriever.METADATA_KEY_DATE]，
     * 通常为 ISO8601，如 "20230815T091530.000Z"）。无 metadata 返回 null。
     */
    fun readDateString(url: String): String?
}

class DefaultVideoMetadataReader @Inject constructor(
    private val quickTimeLocationReader: QuickTimeLocationReader,
) : VideoMetadataReader {

    override fun readLocationString(url: String): String? {
        val platformLocation = try {
            readMetadata(url, MediaMetadataRetriever.METADATA_KEY_LOCATION)
        } catch (_: Exception) {
            null
        }
        return platformLocation ?: quickTimeLocationReader.readLocationString(url)
    }

    override fun readDateString(url: String): String? =
        readMetadata(url, MediaMetadataRetriever.METADATA_KEY_DATE)

    private fun readMetadata(url: String, key: Int): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            // 远程 URL 直接作为数据源，MediaMetadataRetriever 会按需读取 metadata，
            // 不下载整段视频（对比 Java 版被移除的 2MB head/tail 文本搜索 hack）。
            retriever.setDataSource(url, hashMapOf("User-Agent" to "pan.baidu.com"))
            retriever.extractMetadata(key)
        } finally {
            runCatching { retriever.release() }
        }
    }
}
