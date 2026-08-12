package com.baidu.tv.player.kt.location

import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
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
    suspend fun readLocationString(url: String): String?

    /**
     * 从远程视频 URL 读取拍摄日期字符串（[MediaMetadataRetriever.METADATA_KEY_DATE]，
     * 通常为 ISO8601，如 "20230815T091530.000Z"）。无 metadata 返回 null。
     */
    suspend fun readDateString(url: String): String?
}

class DefaultVideoMetadataReader @Inject constructor(
    private val quickTimeLocationReader: QuickTimeLocationReader,
) : VideoMetadataReader {

    override suspend fun readLocationString(url: String): String? {
        Log.d(TAG, "启动 Android metadata 与 QuickTime metadata 并行探测")
        return raceValidLocations(
        platformProbe = {
            runInterruptible(Dispatchers.IO) {
                readMetadata(url, MediaMetadataRetriever.METADATA_KEY_LOCATION)
            }
        },
            quickTimeProbe = { quickTimeLocationReader.readLocationString(url) },
        )
    }

    override suspend fun readDateString(url: String): String? =
        runInterruptible(Dispatchers.IO) {
            readMetadata(url, MediaMetadataRetriever.METADATA_KEY_DATE)
        }

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

    companion object {
        private const val TAG = "VideoMetadataReader"

        internal suspend fun raceValidLocations(
            platformProbe: suspend () -> String?,
            quickTimeProbe: suspend () -> String?,
        ): String? = coroutineScope {
            val results = Channel<String?>(capacity = 2)
            val probes = listOf(
                "AndroidMetadata" to platformProbe,
                "QuickTimeMetadata" to quickTimeProbe,
            ).map { (name, probe) ->
                async {
                    val value = try {
                        probe()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    val validValue = value?.takeIf { LocationExtractor.parseIso6709(it) != null }
                    when {
                        validValue != null -> Log.d(TAG, "视频位置探测命中，来源=$name")
                        value != null -> Log.d(TAG, "视频位置 metadata 不可解析，来源=$name")
                        else -> Log.d(TAG, "视频位置探测无结果，来源=$name")
                    }
                    results.send(validValue)
                }
            }

            try {
                repeat(probes.size) {
                    results.receive()?.let { return@coroutineScope it }
                }
                null
            } finally {
                probes.forEach { it.cancel() }
                results.close()
            }
        }
    }
}
