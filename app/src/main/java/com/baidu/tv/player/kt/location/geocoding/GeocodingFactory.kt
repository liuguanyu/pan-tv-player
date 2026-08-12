package com.baidu.tv.player.kt.location.geocoding

import android.util.Log
import com.baidu.tv.player.kt.location.GpsCoordinate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 逆地理编码工厂（对应 tasks.md 7.8，迁移自 Java 版 GeocodingFactory）。
 *
 * 统一入口：注入全部 [GeocodingStrategy]，并行尝试所有可用策略；任一成功即返回并取消其余探测，
 * 全部失败返回 null（静默回退）。优先级排序仅保留作稳定展示和诊断。
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
     * 并行逆地理编码：返回首个非空地址，并取消其余探测；调用方取消时全部子任务一并取消。
     */
    suspend fun reverseGeocode(coordinate: GpsCoordinate): String? = coroutineScope {
        if (!coordinate.isValid()) return@coroutineScope null
        val availableStrategies = orderedStrategies.filter { it.isAvailable() }
        if (availableStrategies.isEmpty()) return@coroutineScope null

        val results = Channel<Pair<GeocodingStrategy, String?>>(availableStrategies.size)
        val jobs = availableStrategies.map { strategy ->
            launch {
                val address = try {
                    strategy.getAddress(coordinate)
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    Log.w(TAG, "策略 ${strategy.name} 主动取消，按失败处理", e)
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "策略 ${strategy.name} 逆地理编码异常", e)
                    null
                }
                results.send(strategy to address)
            }
        }

        try {
            repeat(availableStrategies.size) {
                val (strategy, address) = results.receive()
                if (!address.isNullOrBlank()) {
                    Log.d(TAG, "逆地理编码成功，策略=${strategy.name}")
                    return@coroutineScope address
                }
            }
            null
        } finally {
            jobs.forEach { it.cancel() }
            results.close()
        }
    }

    /** 暴露当前按优先级排序后的策略名称，便于测试/诊断。 */
    fun strategyOrder(): List<String> = orderedStrategies.map { it.name }

    companion object {
        private const val TAG = "GeocodingFactory"
    }
}
