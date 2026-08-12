package com.baidu.tv.player.kt.location.geocoding

import android.content.Context
import android.location.Geocoder
import com.baidu.tv.player.kt.location.GpsCoordinate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.Locale
import javax.inject.Inject

/**
 * Android 系统内置 Geocoder 逆地理编码策略（优先级中）。
 *
 * - 仅当 [Geocoder.isPresent] 为 true 时可用（依赖设备是否内置后端）；
 * - 使用 WGS84 坐标直接查询（系统 Geocoder 按 WGS84）；
 * - 失败/无结果静默返回 null。
 *
 * 注意：此策略不请求任何定位权限，仅将传入坐标反解为地址。
 */
class AndroidGeocoderStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
) : GeocodingStrategy {

    override val name: String = "AndroidGeocoder"
    override val priority: Int = PRIORITY

    override fun isAvailable(): Boolean = Geocoder.isPresent()

    @Suppress("DEPRECATION")
    override suspend fun getAddress(coordinate: GpsCoordinate): String? {
        if (!isAvailable()) return null
        return try {
            runInterruptible(Dispatchers.IO) {
                val geocoder = Geocoder(context, Locale.CHINESE)
                val results = geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
                val address = results?.firstOrNull() ?: return@runInterruptible null
                buildString {
                    address.adminArea?.let { append(it) }
                    address.locality?.takeIf { it != address.adminArea }?.let { append(it) }
                    address.subLocality?.let { append(it) }
                    address.thoroughfare?.let { append(it) }
                    address.featureName?.takeIf { it != address.thoroughfare }?.let { append(it) }
                }.takeIf { it.isNotBlank() } ?: address.getAddressLine(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val PRIORITY = 2
    }
}
