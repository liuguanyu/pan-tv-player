package com.baidu.tv.player.kt.location.geocoding

import com.baidu.tv.player.kt.config.BaiduConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AmapGeocodingStrategy] 无网络逻辑测试（对应 tasks.md 7.9）。
 *
 * - isAvailable 与当前 key 配置一致：占位符/空 → false，真实 key → true；
 * - WGS84 -> GCJ02 坐标转换在中国境内产生偏移、境外原样返回。
 */
class AmapGeocodingStrategyTest {

    private val strategy = AmapGeocodingStrategy(mockk(relaxed = true))

    @Test
    fun isAvailable_reflectsKeyConfiguration() {
        // 无论本地 local.properties 是否填入真实 key，该断言都应成立：
        // key 为空或占位符 → 不可用；已配置真实 key → 可用。
        val key = BaiduConfig.AMAP_API_KEY
        val expectAvailable = key.isNotBlank() && key != "YOUR_AMAP_API_KEY"
        assertEquals(expectAvailable, strategy.isAvailable())
    }

    @Test
    fun priorityIsHighest() {
        assertEquals(1, strategy.priority)
        assertEquals("Amap", strategy.name)
    }

    @Test
    fun parseAddress_readsRealAmapResponseShape() {
        val body = """
            {
              "status": "1",
              "regeocode": {
                "formatted_address": "天津市河北区建昌道街道艳益路83号",
                "addressComponent": { "province": "天津市", "district": "河北区" }
              },
              "info": "OK",
              "infocode": "10000"
            }
        """.trimIndent()

        assertEquals(
            "天津市河北区建昌道街道艳益路83号",
            AmapGeocodingStrategy.parseAddress(body),
        )
    }

    @Test
    fun parseAddress_returnsNullForFailedResponse() {
        val body = """{"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}"""

        assertEquals(null, AmapGeocodingStrategy.parseAddress(body))
    }

    @Test
    fun wgs84ToGcj02_appliesOffsetWithinChina() {
        val (lat, lon) = AmapGeocodingStrategy.wgs84ToGcj02(39.9042, 116.4074)
        // 中国境内应产生偏移（非原值）。
        assertTrue(kotlin.math.abs(lat - 39.9042) > 1e-4)
        assertTrue(kotlin.math.abs(lon - 116.4074) > 1e-4)
    }

    @Test
    fun wgs84ToGcj02_noOffsetOutsideChina() {
        val (lat, lon) = AmapGeocodingStrategy.wgs84ToGcj02(48.8566, 2.3522) // 巴黎
        assertEquals(48.8566, lat, 1e-9)
        assertEquals(2.3522, lon, 1e-9)
    }
}
