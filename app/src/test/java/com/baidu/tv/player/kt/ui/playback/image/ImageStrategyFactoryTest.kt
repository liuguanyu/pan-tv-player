package com.baidu.tv.player.kt.ui.playback.image

import com.baidu.tv.player.kt.model.ImageEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageStrategyFactoryTest {

    @Test
    fun effectFactory_mapsAllNineJavaEffectValues() {
        assertSame(FadeEffectStrategy, ImageEffectFactory.fromValue(0))
        assertSame(EaseEffectStrategy, ImageEffectFactory.fromValue(1))
        assertSame(FloatEffectStrategy, ImageEffectFactory.fromValue(2))
        assertSame(BounceEffectStrategy, ImageEffectFactory.fromValue(3))
        assertSame(RandomEffectStrategy, ImageEffectFactory.fromValue(4))
        assertSame(BlindsEffectStrategy, ImageEffectFactory.fromValue(5))
        assertSame(ZoomEffectStrategy, ImageEffectFactory.fromValue(6))
        assertSame(RotateEffectStrategy, ImageEffectFactory.fromValue(7))
        assertSame(SlideEffectStrategy, ImageEffectFactory.fromValue(8))
    }

    @Test
    fun effectFactory_resolveRandomReturnsConcreteStrategy() {
        repeat(20) {
            val strategy = ImageEffectFactory.resolve(ImageEffect.RANDOM)
            assertTrue(strategy.effect != ImageEffect.RANDOM)
            assertTrue(strategy in ImageEffectFactory.concreteStrategies.values)
        }
    }

    @Test
    fun backgroundFactory_mapsThreeJavaModes() {
        assertSame(BlackBackgroundStrategy, ImageBackgroundFactory.fromValue(0))
        assertSame(DominantColorBackgroundStrategy, ImageBackgroundFactory.fromValue(1))
        assertSame(BlurBackgroundStrategy, ImageBackgroundFactory.fromValue(2))
        assertSame(BlackBackgroundStrategy, ImageBackgroundFactory.fromValue(99))
    }

    @Test
    fun blindsConstants_matchOriginalJavaView() {
        assertEquals(8, BlindsImageView.BLINDS_COUNT)
        assertEquals(1_000L, BlindsImageView.ANIMATION_DURATION_MS)
    }
}
