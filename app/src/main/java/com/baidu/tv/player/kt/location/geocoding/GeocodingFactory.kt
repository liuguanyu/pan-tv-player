package com.baidu.tv.player.kt.location.geocoding

import android.util.Log
import com.baidu.tv.player.kt.location.GpsCoordinate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 逆地理编码工厂（对应 tasks.md 7.8，迁移自 Java 版 GeocodingFactory）。
 *
 * 统一入口：注入全部 [GeocodingStrategy]，按 [GeocodingStrategy.priority] 升序（优先级高→低）排序，
 * 逐个尝试"可用且能返回结果"的策略；任一成功即返回，全部失败返回 null（静默回退）。
 *
 * 策略集合由 [com.baidu.tv.player.kt.di.GeocodingModule] 通过 Multibinding 提供，
 * 避免任何手动 getInstance 单例。
 */
@Singleton
class GeocodingFactory @Inject constructor(
    strategies: Set<@JvmSuppressWildcards GeocodingStrategy>,
) {

    /** 按优先级升序排列（数值越小越靠前）。 */
    private val orderedStrategies: List<GeocodingStrategy> =
        strategies.sortedBy { it.priority }

    /**
     * 逆地理编码：按优先级尝试所有可用策略，返回首个非空地址；全部失败返回 null。
     */
    suspend fun reverseGeocode(coordinate: GpsCoordinate): String? {
        if (!coordinate.isValid()) return null
        for (strategy in orderedStrategies) {
            if (!strategy.isAvailable()) continue
            val address = runCatching { strategy.getAddress(coordinate) }
                .onFailure { Log.w(TAG, "策略 ${strategy.name} 逆地理编码异常，回退", it) }
                .getOrNull()
            if (!address.isNullOrBlank()) {
                Log.d(TAG, "逆地理编码成功，策略=${strategy.name}")
                return address
            }
        }
        return null
    }

    /** 暴露当前按优先级排序后的策略名称，便于测试/诊断。 */
    fun strategyOrder(): List<String> = orderedStrategies.map { it.name }

    companion object {
        private const val TAG = "GeocodingFactory"
    }
}
