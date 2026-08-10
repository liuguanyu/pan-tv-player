package com.baidu.tv.player.kt.model

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * 枚举映射单测：MediaType / PlayMode / ImageEffect 的 fromCode/fromValue 往返 + RANDOM 转换。
 */
class EnumsTest {

    @Test
    fun mediaType_fromCode_roundTrip() {
        assertEquals(MediaType.IMAGE, MediaType.fromCode(1))
        assertEquals(MediaType.VIDEO, MediaType.fromCode(2))
        assertEquals(MediaType.ALL, MediaType.fromCode(3))
    }

    @Test
    fun mediaType_fromCode_unknown_defaultsToAll() {
        assertEquals(MediaType.ALL, MediaType.fromCode(99))
        assertEquals(MediaType.ALL, MediaType.fromCode(0))
    }

    @Test
    fun mediaType_value_equalsCode() {
        assertEquals(MediaType.IMAGE.code, MediaType.IMAGE.value)
    }

    @Test
    fun playMode_fromValue_roundTrip() {
        assertEquals(PlayMode.SEQUENTIAL, PlayMode.fromValue(0))
        assertEquals(PlayMode.RANDOM, PlayMode.fromValue(1))
        assertEquals(PlayMode.SINGLE, PlayMode.fromValue(2))
        assertEquals(PlayMode.REVERSE, PlayMode.fromValue(3))
    }

    @Test
    fun playMode_fromValue_unknown_defaultsToSequential() {
        assertEquals(PlayMode.SEQUENTIAL, PlayMode.fromValue(999))
    }

    @Test
    fun imageEffect_fromValue_roundTrip() {
        assertEquals(ImageEffect.FADE, ImageEffect.fromValue(0))
        assertEquals(ImageEffect.RANDOM, ImageEffect.fromValue(4))
        assertEquals(ImageEffect.SLIDE, ImageEffect.fromValue(8))
    }

    @Test
    fun imageEffect_fromValue_unknown_defaultsToFade() {
        assertEquals(ImageEffect.FADE, ImageEffect.fromValue(-1))
    }

    @Test
    fun imageEffect_getActualEffect_returnsSelf_forConcreteEffects() {
        assertEquals(ImageEffect.FADE, ImageEffect.FADE.getActualEffect())
        assertEquals(ImageEffect.ZOOM, ImageEffect.ZOOM.getActualEffect())
    }

    @Test
    fun imageEffect_getActualEffect_returnsConcrete_forRandom() {
        val actual = ImageEffect.RANDOM.getActualEffect()
        assertTrue(actual in listOf(
            ImageEffect.FADE, ImageEffect.EASE, ImageEffect.FLOAT, ImageEffect.BOUNCE,
            ImageEffect.BLINDS, ImageEffect.ZOOM, ImageEffect.ROTATE, ImageEffect.SLIDE,
        ))
        // RANDOM 不应解析为自身
        assertTrue(actual != ImageEffect.RANDOM)
    }

    @Test
    fun imageEffect_getRandomEffect_neverReturnsRandom() {
        repeat(50) {
            assertTrue(ImageEffect.getRandomEffect() != ImageEffect.RANDOM)
        }
    }
}
