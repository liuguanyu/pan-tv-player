package com.baidu.tv.player.kt.location.geocoding

import com.baidu.tv.player.kt.location.GpsCoordinate

/**
 * 逆地理编码策略接口（对应 tasks.md 7.8，迁移自 Java 版 GeocodingStrategy）。
 *
 * 将 WGS84 [GpsCoordinate] 反解为人类可读地址。所有实现必须：
 * - 为 suspend 函数，运行在 IO 调度器（由调用方切换）；
 * - 失败/无结果时静默返回 null，绝不抛出异常给上层；
 * - 不请求任何设备定位权限，仅根据传入坐标查询。
 */
interface GeocodingStrategy {

    /** 策略名称（用于日志/选择）。 */
    val name: String

    /**
     * 优先级，数值越小优先级越高。工厂按此排序进行回退。
     */
    val priority: Int

    /**
     * 当前环境下该策略是否可用（如 API key 是否配置、Geocoder 是否 present）。
     */
    fun isAvailable(): Boolean

    /**
     * 逆地理编码：返回格式化地址；失败或无结果返回 null。
     */
    suspend fun getAddress(coordinate: GpsCoordinate): String?
}
