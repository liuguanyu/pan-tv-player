package com.baidu.tv.player.kt.location.geocoding

import com.baidu.tv.player.kt.config.BaiduConfig
import com.baidu.tv.player.kt.location.GpsCoordinate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 高德地图逆地理编码策略（优先级最高）。
 *
 * - 仅当 [BaiduConfig.AMAP_API_KEY] 已配置（非占位符）时可用；
 * - 高德使用 GCJ02 坐标系，需将 EXIF 的 WGS84 先转 GCJ02（[wgs84ToGcj02]）；
 * - 失败/无结果静默返回 null。
 */
class AmapGeocodingStrategy @Inject constructor(
    private val okHttpClient: OkHttpClient,
) : GeocodingStrategy {

    override val name: String = "Amap"
    override val priority: Int = PRIORITY

    override fun isAvailable(): Boolean {
        val key = BaiduConfig.AMAP_API_KEY
        return key.isNotBlank() && key != PLACEHOLDER_KEY
    }

    override suspend fun getAddress(coordinate: GpsCoordinate): String? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        runCatching {
            val (gcjLat, gcjLon) = wgs84ToGcj02(coordinate.latitude, coordinate.longitude)
            val url = "https://restapi.amap.com/v3/geocode/regeo" +
                "?key=${BaiduConfig.AMAP_API_KEY}" +
                "&location=$gcjLon,$gcjLat" +
                "&extensions=base&output=json"
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                if (json.optString("status") != "1") return@use null
                val regeocode = json.optJSONObject("regeocode") ?: return@use null
                regeocode.optString("formatted_address").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        const val PRIORITY = 1
        private const val PLACEHOLDER_KEY = "YOUR_AMAP_API_KEY"

        // WGS84 -> GCJ02 转换（火星坐标偏移），迁移自 Java 版。
        private const val PI = 3.1415926535897932384626
        private const val A = 6378245.0
        private const val EE = 0.00669342162296594323

        fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
            if (outOfChina(lat, lon)) return lat to lon
            var dLat = transformLat(lon - 105.0, lat - 35.0)
            var dLon = transformLon(lon - 105.0, lat - 35.0)
            val radLat = lat / 180.0 * PI
            var magic = sin(radLat)
            magic = 1 - EE * magic * magic
            val sqrtMagic = sqrt(magic)
            dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
            dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
            return (lat + dLat) to (lon + dLon)
        }

        private fun outOfChina(lat: Double, lon: Double): Boolean =
            lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

        private fun transformLat(x: Double, y: Double): Double {
            var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(kotlin.math.abs(x))
            ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
            ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
            ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
            return ret
        }

        private fun transformLon(x: Double, y: Double): Double {
            var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(kotlin.math.abs(x))
            ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
            ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
            ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
            return ret
        }
    }
}
