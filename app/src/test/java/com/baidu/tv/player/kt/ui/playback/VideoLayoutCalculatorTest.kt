package com.baidu.tv.player.kt.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoLayoutCalculatorTest {

    @Test
    fun fitCenter_landscape16By9_fillsTvScreen() {
        assertEquals(
            VideoLayoutCalculator.Size(1920, 1080),
            VideoLayoutCalculator.fitCenter(3840, 2160, 1920, 1080),
        )
    }

    @Test
    fun fitCenter_portraitVideo_fillsHeightAndKeepsRatio() {
        assertEquals(
            VideoLayoutCalculator.Size(608, 1080),
            VideoLayoutCalculator.fitCenter(1080, 1920, 1920, 1080),
        )
    }

    @Test
    fun fitCenter_fourByThreeVideo_hasPillarBox() {
        assertEquals(
            VideoLayoutCalculator.Size(1440, 1080),
            VideoLayoutCalculator.fitCenter(1440, 1080, 1920, 1080),
        )
    }

    @Test
    fun fitCenter_anamorphicWidthAlreadyAdjustedByEngine_keepsDisplayRatio() {
        assertEquals(
            VideoLayoutCalculator.Size(1920, 1080),
            VideoLayoutCalculator.fitCenter(1920, 1080, 1920, 1080),
        )
    }

    @Test
    fun fitCenter_invalidSize_returnsNull() {
        assertNull(VideoLayoutCalculator.fitCenter(0, 1080, 1920, 1080))
    }
}
