package com.baidu.tv.player.kt.ui.playback

import kotlin.math.roundToInt

/** 计算视频在容器内保持显示宽高比的 fit-center 尺寸。 */
internal object VideoLayoutCalculator {

    data class Size(val width: Int, val height: Int)

    fun fitCenter(
        videoWidth: Int,
        videoHeight: Int,
        containerWidth: Int,
        containerHeight: Int,
    ): Size? {
        if (videoWidth <= 0 || videoHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            return null
        }
        val scale = minOf(
            containerWidth.toDouble() / videoWidth,
            containerHeight.toDouble() / videoHeight,
        )
        return Size(
            width = (videoWidth * scale).roundToInt().coerceIn(1, containerWidth),
            height = (videoHeight * scale).roundToInt().coerceIn(1, containerHeight),
        )
    }
}
