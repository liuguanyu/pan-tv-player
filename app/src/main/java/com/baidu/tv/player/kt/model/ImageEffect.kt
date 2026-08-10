package com.baidu.tv.player.kt.model

import kotlin.random.Random

/**
 * 图片展示特效。注意 RANDOM(4) 的 value 与顺序不同（沿用 Java 版约定）。
 */
enum class ImageEffect(val value: Int, val displayName: String) {
    FADE(0, "淡入淡出"),
    EASE(1, "缓动"),
    FLOAT(2, "浮现"),
    BOUNCE(3, "跳动"),
    RANDOM(4, "随机"),
    BLINDS(5, "百叶窗"),
    ZOOM(6, "放大"),
    ROTATE(7, "旋转"),
    SLIDE(8, "两侧划入");

    /**
     * 若当前特效为 RANDOM，返回随机一个具体特效（8 种之一）；否则返回自身。
     */
    fun getActualEffect(): ImageEffect =
        if (this == RANDOM) getRandomEffect() else this

    companion object {
        /** 随机可用的具体特效（不含 RANDOM 本身）。 */
        private val CONCRETE_EFFECTS =
            arrayOf(FADE, EASE, FLOAT, BOUNCE, BLINDS, ZOOM, ROTATE, SLIDE)

        /** 返回一个随机具体特效。 */
        @JvmStatic
        fun getRandomEffect(): ImageEffect = CONCRETE_EFFECTS[Random.nextInt(CONCRETE_EFFECTS.size)]

        @JvmStatic
        fun fromValue(value: Int): ImageEffect =
            entries.firstOrNull { it.value == value } ?: FADE
    }
}
