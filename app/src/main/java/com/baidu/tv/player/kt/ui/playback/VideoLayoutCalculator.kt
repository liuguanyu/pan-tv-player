package com.baidu.tv.player.kt.ui.playback

import kotlin.math.roundToInt

/** 计算视频在容器内保持显示宽高比的 fit-center 尺寸。 */
internal object VideoLayoutCalculator {

    data class Size(val width: Int, val height: Int)

    data class Layout(
        /** SurfaceView 旋转后的视觉尺寸。 */
        val visualSize: Size,
        /** 旋转前应设置给 SurfaceView 的布局尺寸。 */
        val surfaceSize: Size,
    )

    fun fitCenter(
        videoWidth: Int,
        videoHeight: Int,
        containerWidth: Int,
        containerHeight: Int,
    ): Size? = fitCenterLayout(videoWidth, videoHeight, containerWidth, containerHeight, 0)?.visualSize

    fun fitCenterLayout(
        videoWidth: Int,
        videoHeight: Int,
        containerWidth: Int,
        containerHeight: Int,
        rotationDegrees: Int,
    ): Layout? {
        if (videoWidth <= 0 || videoHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            return null
        }
        val rotation = ((rotationDegrees % 360) + 360) % 360
        val quarterTurn = rotation == 90 || rotation == 270
        val visualWidth = if (quarterTurn) videoHeight else videoWidth
        val visualHeight = if (quarterTurn) videoWidth else videoHeight
        val scale = minOf(
            containerWidth.toDouble() / visualWidth,
            containerHeight.toDouble() / visualHeight,
        )
        val visualSize = Size(
            width = (visualWidth * scale).roundToInt().coerceIn(1, containerWidth),
            height = (visualHeight * scale).roundToInt().coerceIn(1, containerHeight),
        )
        return Layout(
            visualSize = visualSize,
            surfaceSize = if (quarterTurn) {
                Size(visualSize.height, visualSize.width)
            } else {
                visualSize
            },
        )
    }
}
