package com.baidu.tv.player.kt.player

import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

private const val TAG = "CodecInspector"
private const val INSPECT_TIMEOUT_MS = 8_000L

/**
 * [VideoCodecInspector] 默认实现（对应 tasks 6.4）。
 *
 * 使用 [MediaMetadataRetriever] 读取远端媒体的 mime / 宽 / 高，
 * 通过 [withTimeout] 包装（design.md 痛点 #4：百度 CDN 重定向可能无限阻塞）。
 *
 * 注意：10-bit 检测在纯 `MediaMetadataRetriever` 上无直接字段，这里返回 8-bit，
 * 由 [Media3VideoPlayerEngine] 在硬解阶段结合 Media3 的 `Format` color info 进一步识别，
 * 或由播放前调用方传入已知色深；这是 MediaMetadataRetriever API 的固有限制。
 */
class MediaMetadataRetrieverCodecInspector @Inject constructor() : VideoCodecInspector {

    override suspend fun inspect(url: String, headers: Map<String, String>): VideoCodecInfo =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                withTimeout(INSPECT_TIMEOUT_MS) {
                    retriever.setDataSource(url, headers)
                    val metadataMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    val width = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    val height = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0
                    val trackInfo = inspectVideoTrack(url, headers)
                    VideoCodecInfo(
                        mimeType = trackInfo?.mimeType ?: metadataMime ?: normalizeVideoMime(url),
                        width = trackInfo?.width?.takeIf { it > 0 } ?: width,
                        height = trackInfo?.height?.takeIf { it > 0 } ?: height,
                        bitDepth = trackInfo?.bitDepth ?: 8,
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "编码检测超时: $url")
                VideoCodecInfo.UNKNOWN
            } catch (e: Exception) {
                Log.e(TAG, "编码检测失败: ${e.message}")
                VideoCodecInfo.UNKNOWN
            } finally {
                runCatching { retriever.release() }
            }
        }

    private fun inspectVideoTrack(url: String, headers: Map<String, String>): TrackInfo? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(url, headers)
            var fallback: TrackInfo? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (!mime.startsWith("video/")) continue
                val info = format.toTrackInfo(mime)
                // DV 片源常同时暴露 dolby-vision 与 hevc 两条视频轨；优先返回 DV 轨，
                // 避免只看第一条 hevc 轨而漏判（导致 MediaCodec 运行时 Error 0xe）。
                if (info.mimeType == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) return info
                if (fallback == null) fallback = info
            }
            fallback
        } catch (e: Exception) {
            Log.w(TAG, "视频轨道检测失败: ${e.message}")
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.toTrackInfo(mime: String): TrackInfo {
        // API 30+ 标准键 "codecs-string"；旧版部分厂商用 "codecs"。
        val codecs = getString("codecs-string") ?: getString("codecs")
        val resolvedMime = when {
            mime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> mime
            codecs.isDolbyVisionCodecString() -> MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION
            else -> mime
        }
        return TrackInfo(
            mimeType = resolvedMime,
            width = getIntegerOrDefault(MediaFormat.KEY_WIDTH),
            height = getIntegerOrDefault(MediaFormat.KEY_HEIGHT),
            bitDepth = resolveBitDepth(mime, codecs),
        )
    }

    /**
     * 推断色深：优先 "bit-depth" / HEVC Main10 profile / HDR 传递函数（HLG/PQ 必为 10-bit），
     * DV codec 字符串亦按 10-bit 处理；均未命中按 8-bit。
     */
    private fun MediaFormat.resolveBitDepth(mime: String, codecs: String?): Int {
        getIntegerOrDefault("bit-depth").takeIf { it > 0 }?.let { return it }
        if (codecs.isDolbyVisionCodecString()) return 10
        if (mime == MediaFormat.MIMETYPE_VIDEO_HEVC &&
            getIntegerOrDefault(MediaFormat.KEY_PROFILE) == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
        ) {
            return 10
        }
        val transfer = getIntegerOrDefault(MediaFormat.KEY_COLOR_TRANSFER)
        if (transfer == MediaFormat.COLOR_TRANSFER_HLG || transfer == MediaFormat.COLOR_TRANSFER_ST2084) {
            return 10
        }
        return 8
    }

    private fun MediaFormat.getIntegerOrDefault(key: String): Int =
        if (containsKey(key)) getInteger(key) else 0

    private fun String?.isDolbyVisionCodecString(): Boolean {
        val value = this?.lowercase() ?: return false
        return value.startsWith("dvhe") ||
            value.startsWith("dvh1") ||
            value.startsWith("hev1.08") ||
            value.startsWith("hvc1.08")
    }

    private data class TrackInfo(
        val mimeType: String,
        val width: Int,
        val height: Int,
        val bitDepth: Int,
    )

    /** MIMETYPE 缺失时，从容器 mime 无法直接得到编码，返回 null 让上层按“非 HEVC 直接播放”处理。 */
    private fun normalizeVideoMime(url: String): String? = null

    companion object {
        /** 常见视频 MIME 常量（供测试与判定引用）。 */
        const val MIME_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC
        const val MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC
    }
}
