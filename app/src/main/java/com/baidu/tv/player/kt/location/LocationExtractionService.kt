package com.baidu.tv.player.kt.location

import android.util.Log
import com.baidu.tv.player.kt.location.geocoding.GeocodingFactory
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 地点识别用例（对应 tasks.md 7.4）。
 *
 * 组合 [LocationExtractor]（媒体元数据 GPS 提取）与 [GeocodingFactory]（逆地理编码），
 * 对外提供"给定媒体 URL → 人类可读地址"的单一挂起入口。
 *
 * 替代 Java 版基于 IntentService + ResultReceiver + 独立进程的实现：
 * 改用协程挂起函数，由调用方（ViewModel）在 viewModelScope 中调度，
 * 无 Thread / Handler / 独立进程，符合 design.md 并发约束。
 *
 * 全流程失败静默返回 null，绝不弹错误、不请求定位权限。
 */
@Singleton
class LocationExtractionService @Inject constructor(
    private val locationExtractor: LocationExtractor,
    private val geocodingFactory: GeocodingFactory,
) {

    /**
     * 提取媒体地点。
     *
     * @param url 媒体（图片或视频）下载 URL。
     * @param isVideo true 表示视频（走 MediaMetadataRetriever），false 表示图片（走 EXIF Range 请求）。
     * @return 人类可读地址；无 GPS / 无法解析 / 任意失败均返回 null。
     */
    suspend fun extractLocation(url: String, isVideo: Boolean, fileNameHint: String? = null): String? {
        val coordinate = if (isVideo) {
            locationExtractor.extractVideoGps(url, fileNameHint)
        } else {
            locationExtractor.extractImageGps(url)
        }
        if (coordinate == null || !coordinate.isValid()) {
            Log.d(TAG, "未从媒体元数据获取到有效 GPS，返回 null")
            return null
        }
        return geocodingFactory.reverseGeocode(coordinate)
    }

    /**
     * 提取媒体拍摄时间。
     *
     * @param url 媒体（图片或视频）下载 URL。
     * @param isVideo true 走视频元数据（MediaMetadataRetriever），false 走图片 EXIF Range 请求。
     * @return 归一化后的拍摄时间字符串（"yyyy-MM-dd HH:mm"）；无元数据 / 任意失败均返回 null。
     */
    suspend fun extractCaptureTime(url: String, isVideo: Boolean, fileNameHint: String? = null): String? {
        val metadataTime = if (isVideo) {
            locationExtractor.extractVideoDateTime(url)
        } else {
            locationExtractor.extractImageDateTime(url)
        }
        if (!metadataTime.isNullOrBlank()) return metadataTime

        val filenameTime = parseFilenameDateTime(fileNameHint)
        if (filenameTime != null) {
            Log.d(TAG, "媒体 metadata 无拍摄时间，使用文件名降级结果")
            return filenameTime
        }
        Log.d(TAG, "未从媒体元数据或文件名获取到拍摄时间，返回 null")
        return null
    }

    /**
     * 文件名时间仅作为低置信度兜底，不覆盖媒体 metadata。
     * 支持 yyyyMMdd[_-]HHmmss、yyyy-MM-dd[_ ]HH-mm-ss，以及只有日期的形式。
     */
    private fun parseFilenameDateTime(fileName: String?): String? {
        val name = fileName?.substringAfterLast('/') ?: return null
        val match = Regex("(?<!\\d)(20\\d{2})[-_ .]?(\\d{2})[-_ .]?(\\d{2})(?:[^\\d]?(\\d{2})[-_:.]?(\\d{2})[-_:.]?(\\d{2}))?(?!\\d)")
            .find(name) ?: return null
        val groups = match.groupValues
        val raw = if (groups[4].isNotEmpty()) {
            "${groups[1]}${groups[2]}${groups[3]}${groups[4]}${groups[5]}${groups[6]}"
        } else {
            "${groups[1]}${groups[2]}${groups[3]}000000"
        }
        return runCatching {
            val parser = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { isLenient = false }
            val date = parser.parse(raw) ?: return@runCatching null
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(date)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "LocationExtractionSvc"
    }
}
