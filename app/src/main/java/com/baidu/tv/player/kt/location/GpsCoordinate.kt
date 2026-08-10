package com.baidu.tv.player.kt.location

/**
 * 媒体元数据中解析出的 GPS 坐标（WGS84）。
 *
 * 仅来源于图片 EXIF 或视频 metadata，不涉及任何设备定位（见 design.md Decision 11）。
 */
data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    /** 简单有效性校验：经纬度范围合法且非 (0,0)。 */
    fun isValid(): Boolean =
        latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            !(latitude == 0.0 && longitude == 0.0)
}
