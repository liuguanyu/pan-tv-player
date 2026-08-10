package com.baidu.tv.player.kt.location

import android.util.Log
import com.baidu.tv.player.kt.location.geocoding.GeocodingFactory
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
    suspend fun extractLocation(url: String, isVideo: Boolean): String? {
        val coordinate = if (isVideo) {
            locationExtractor.extractVideoGps(url)
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
    suspend fun extractCaptureTime(url: String, isVideo: Boolean): String? {
        val time = if (isVideo) {
            locationExtractor.extractVideoDateTime(url)
        } else {
            locationExtractor.extractImageDateTime(url)
        }
        if (time.isNullOrBlank()) {
            Log.d(TAG, "未从媒体元数据获取到拍摄时间，返回 null")
            return null
        }
        return time
    }

    companion object {
        private const val TAG = "LocationExtractionSvc"
    }
}
