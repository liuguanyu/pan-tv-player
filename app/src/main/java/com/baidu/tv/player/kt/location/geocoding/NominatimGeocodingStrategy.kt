package com.baidu.tv.player.kt.location.geocoding

import com.baidu.tv.player.kt.location.GpsCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject

/**
 * OpenStreetMap Nominatim 逆地理编码策略（优先级最低，作为兜底）。
 *
 * - 无需 API key，始终可用；
 * - 使用 WGS84 坐标；
 * - 必须携带合规 User-Agent；
 * - 失败/无结果静默返回 null。
 */
class NominatimGeocodingStrategy @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : GeocodingStrategy {

    override val name: String = "Nominatim"
    override val priority: Int = PRIORITY

    override fun isAvailable(): Boolean = true

    override suspend fun getAddress(coordinate: GpsCoordinate): String? = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://nominatim.openstreetmap.org/reverse" +
                "?format=json" +
                "&lat=${coordinate.latitude}" +
                "&lon=${coordinate.longitude}" +
                "&accept-language=zh"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                json.optString("display_name").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        const val PRIORITY = 3
        private const val USER_AGENT = "BaiduTVPlayer/1.0"
    }
}
