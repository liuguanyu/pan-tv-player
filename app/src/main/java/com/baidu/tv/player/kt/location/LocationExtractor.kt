package com.baidu.tv.player.kt.location

import android.media.ExifInterface
import android.util.Log
import com.baidu.tv.player.kt.location.geocoding.executeCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 从远程媒体元数据中提取 GPS 坐标（对应 tasks.md 7.5 / 7.6 / 7.7）。
 *
 * 隐私 / 性能约束（design.md Decision 11）：
 * - 不申请、不使用任何设备定位权限；仅解析媒体自带元数据。
 * - 图片：HTTP `Range: bytes=0-131071` 只读取前 128KB 头部，用 [ExifInterface] 解析 EXIF GPS；
 *   绝不整图下载（对比被移除的 Java 版 10MB 整图下载）。
 * - 视频：使用 [VideoMetadataReader]（MediaMetadataRetriever）+ [withTimeout] 8 秒；
 *   超时/异常/无 metadata 静默返回 null（对比被移除的 Java 版 2MB head/tail 文本搜索 hack）。
 */
@Singleton
class LocationExtractor @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val videoMetadataReader: VideoMetadataReader,
) {

    /**
     * 提取图片 GPS。仅下载前 128KB 头部；失败/无 GPS 静默返回 null。
     */
    suspend fun extractImageGps(url: String): GpsCoordinate? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                // 只请求前 128KB（0..131071 共 131072 字节），足以覆盖 EXIF 头部。
                .header("Range", "bytes=0-${IMAGE_HEADER_MAX_BYTES - 1}")
                .get()
                .build()
            okHttpClient.executeCancellable(request).use { response ->
                if (!response.isSuccessful) return@use null
                val source = response.body?.source() ?: return@use null
                // 硬上限 128KB：即使服务器忽略 Range 返回整图，也只读取头部即止。
                val bytes = source.readByteArray(
                    minOf(response.body?.contentLength() ?: IMAGE_HEADER_MAX_BYTES.toLong(), IMAGE_HEADER_MAX_BYTES.toLong()),
                )
                parseExifGps(bytes)
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.d(TAG, "图片 GPS 提取失败，静默返回 null: ${it.message}")
            null
        }
    }

    /**
     * 提取视频 GPS。MediaMetadataRetriever + withTimeout(8000)；超时/异常/无 metadata 静默 null。
     */
    suspend fun extractVideoGps(url: String, fileNameHint: String? = null): GpsCoordinate? = withContext(Dispatchers.IO) {
        try {
            withTimeout(VIDEO_TIMEOUT_MS) {
                val iso6709 = videoMetadataReader.readLocationString(url, fileNameHint) ?: return@withTimeout null
                parseIso6709(iso6709)
            }
        } catch (e: TimeoutCancellationException) {
            Log.d(TAG, "视频 GPS 提取超时(${VIDEO_TIMEOUT_MS}ms)，静默返回 null")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "视频 GPS 提取异常，静默返回 null: ${e.message}")
            null
        }
    }

    /**
     * 提取图片拍摄时间。复用 128KB 头部下载（与 GPS 同一 EXIF 头部）；失败/无时间静默返回 null。
     * 返回归一化后的 "yyyy-MM-dd HH:mm"（无法解析则原样返回 EXIF 字符串）。
     */
    suspend fun extractImageDateTime(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-${IMAGE_HEADER_MAX_BYTES - 1}")
                .get()
                .build()
            okHttpClient.executeCancellable(request).use { response ->
                if (!response.isSuccessful) return@use null
                val source = response.body?.source() ?: return@use null
                val bytes = source.readByteArray(
                    minOf(response.body?.contentLength() ?: IMAGE_HEADER_MAX_BYTES.toLong(), IMAGE_HEADER_MAX_BYTES.toLong()),
                )
                parseExifDateTime(bytes)
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.d(TAG, "图片拍摄时间提取失败，静默返回 null: ${it.message}")
            null
        }
    }

    /**
     * 提取视频拍摄时间。MediaMetadataRetriever + withTimeout(8000)；超时/异常/无 metadata 静默 null。
     */
    suspend fun extractVideoDateTime(url: String): String? = withContext(Dispatchers.IO) {
        try {
            withTimeout(VIDEO_TIMEOUT_MS) {
                val raw = videoMetadataReader.readDateString(url) ?: return@withTimeout null
                normalizeDateTime(raw)
            }
        } catch (e: TimeoutCancellationException) {
            Log.d(TAG, "视频拍摄时间提取超时(${VIDEO_TIMEOUT_MS}ms)，静默返回 null")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "视频拍摄时间提取异常，静默返回 null: ${e.message}")
            null
        }
    }

    private fun parseExifDateTime(bytes: ByteArray): String? = runCatching {
        val exifBytes = buildMinimalExifJpeg(bytes) ?: bytes
        val exif = ExifInterface(ByteArrayInputStream(exifBytes))
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            ?: return@runCatching null
        normalizeDateTime(raw)
    }.getOrNull()

    private fun parseExifGps(bytes: ByteArray): GpsCoordinate? = runCatching {
        // 只下载了前 128KB，JPEG 图像数据被截断，直接交给 ExifInterface 会抛
        // "Invalid JPEG segment"。先提取 APP1(EXIF) 段拼装成最小合法 JPEG 再解析；
        // 非 JPEG 或未找到 EXIF 段时回退用原始字节尝试。
        val exifBytes = buildMinimalExifJpeg(bytes) ?: bytes
        val exif = ExifInterface(ByteArrayInputStream(exifBytes))
        val latLong = FloatArray(2)
        @Suppress("DEPRECATION")
        val hasLatLong = exif.getLatLong(latLong)
        if (!hasLatLong) return@runCatching null
        GpsCoordinate(latLong[0].toDouble(), latLong[1].toDouble()).takeIf { it.isValid() }
    }.getOrNull()

    /**
     * 从（可能被截断的）JPEG 头部字节中提取 APP1(EXIF) 段，
     * 拼装成 SOI + APP1 + EOI 的最小合法 JPEG，供 [ExifInterface] 解析。
     *
     * @return 最小 JPEG 字节；非 JPEG / 无完整 APP1 段返回 null。
     */
    private fun buildMinimalExifJpeg(bytes: ByteArray): ByteArray? {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        var offset = 2
        while (offset + 4 <= bytes.size) {
            if (bytes[offset] != 0xFF.toByte()) return null
            val marker = bytes[offset + 1].toInt() and 0xFF
            // 进入压缩数据（SOS）或文件结束（EOI）仍未见 EXIF 段，放弃。
            if (marker == 0xDA || marker == 0xD9) return null
            val length = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
            val segmentEnd = offset + 2 + length
            if (length < 2 || segmentEnd > bytes.size) return null
            if (marker == 0xE1) {
                return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
                    bytes.copyOfRange(offset, segmentEnd) +
                    byteArrayOf(0xFF.toByte(), 0xD9.toByte())
            }
            offset = segmentEnd
        }
        return null
    }

    companion object {
        private const val TAG = "LocationExtractor"

        /**
         * 将 EXIF / 视频元数据的拍摄时间字符串归一化为 "yyyy-MM-dd HH:mm"。
         *
         * 兼容两类常见格式，并正确处理时区：
         * - EXIF：`yyyy:MM:dd HH:mm:ss`（如 "2023:08:15 09:15:30"）。EXIF 时间通常为**拍摄地
         *   本地时间**且不带时区标记，直接展示，不做偏移。
         * - 视频 METADATA_KEY_DATE(ISO8601)：`yyyyMMdd'T'HHmmss` 可带 `.SSS` 与 `Z`
         *   （如 "20230815T091530.000Z"）。**结尾 `Z` 表示 UTC**，需转换到设备默认时区
         *   （如东八区 +8h）后再展示，否则会比实际拍摄时间少 8 小时。
         *
         * 无法解析时返回去空白后的原串（保证有值即展示，不因格式差异丢失信息）。
         */
        fun normalizeDateTime(raw: String): String? {
            val trimmed = raw.trim().takeIf { it.isNotEmpty() } ?: return null
            // 过滤 QuickTime/MP4 "零时间戳"：无真实拍摄时间的视频元数据常回退到 1904-01-01
            // (QuickTime 纪元) 或 1970-01-01 (Unix 纪元)。年份早于 1990 视为无效，直接丢弃。
            if (isInvalidEpochDate(trimmed)) {
                return null
            }
            // 带 UTC 标记（Z 结尾）的 ISO8601 视频时间：按 UTC 解析后转设备默认时区展示。
            if (trimmed.endsWith("Z", ignoreCase = true)) {
                parseUtcIso8601ToLocal(trimmed)?.let { return it }
            }
            // 其余（EXIF 本地时间 / 无时区标记）：提取数字按 年月日时分 组装，不做时区偏移。
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length >= 12) {
                val year = digits.substring(0, 4)
                val month = digits.substring(4, 6)
                val day = digits.substring(6, 8)
                val hour = digits.substring(8, 10)
                val minute = digits.substring(10, 12)
                // 简单合法性校验，非法则回退原串。
                if (month in "01".."12" && day in "01".."31" && hour <= "23" && minute <= "59") {
                    return "$year-$month-$day $hour:$minute"
                }
            }
            return trimmed
        }

        /**
         * 判断拍摄时间字符串是否为无效的"纪元零时间戳"。
         *
         * 部分视频缺少真实拍摄时间时，[MediaMetadataRetriever] 会回退到容器纪元起点：
         * - QuickTime/MP4：1904-01-01（转东八区显示为 "1904-01-01 08:00"）
         * - Unix：1970-01-01
         *
         * 这些时间对用户无意义，应视为"无拍摄时间"直接丢弃。判定规则：提取到的年份 < 1990。
         */
        private fun isInvalidEpochDate(raw: String): Boolean {
            val digits = raw.filter { it.isDigit() }
            if (digits.length < 4) return false
            val year = digits.substring(0, 4).toIntOrNull() ?: return false
            return year < 1990
        }

        /**
         * 将带 `Z`（UTC）的 ISO8601 视频拍摄时间转换为设备默认时区的 "yyyy-MM-dd HH:mm"。
         *
         * 输入形如 "20230815T091530.000Z" 或 "20230815T091530Z"：先按 UTC 解析毫秒时间戳，
         * 再用设备默认时区（[java.util.TimeZone.getDefault]）格式化，从而修正 8 小时时差。
         * 解析失败返回 null，交由调用方回退到不偏移的数字组装逻辑。
         */
        private fun parseUtcIso8601ToLocal(raw: String): String? {
            val patterns = listOf("yyyyMMdd'T'HHmmss.SSS'Z'", "yyyyMMdd'T'HHmmss'Z'")
            for (pattern in patterns) {
                val parsed = runCatching {
                    val parser = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                    parser.parse(raw)
                }.getOrNull()
                if (parsed != null) {
                    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getDefault()
                    }
                    return formatter.format(parsed)
                }
            }
            return null
        }

        /** 图片头部下载上限：128KB。 */
        const val IMAGE_HEADER_MAX_BYTES = 131_072

        /** 视频 GPS 提取超时：8 秒。 */
        const val VIDEO_TIMEOUT_MS = 8_000L

        /**
         * 解析 ISO6709 位置字符串（如 "+34.0522-118.2437/"）为 [GpsCoordinate]。
         * 无法解析返回 null。
         */
        fun parseIso6709(raw: String): GpsCoordinate? = runCatching {
            val trimmed = raw.trim().removeSuffix("/")
            // 匹配形如 +DD.DDDD-DDD.DDDD 的经纬度对（首个为纬度，第二个为经度，含符号）。
            val regex = Regex("([+\\-]\\d+(?:\\.\\d+)?)([+\\-]\\d+(?:\\.\\d+)?)")
            val match = regex.find(trimmed) ?: return@runCatching null
            val lat = match.groupValues[1].toDouble()
            val lon = match.groupValues[2].toDouble()
            GpsCoordinate(lat, lon).takeIf { it.isValid() }
        }.getOrNull()
    }
}
