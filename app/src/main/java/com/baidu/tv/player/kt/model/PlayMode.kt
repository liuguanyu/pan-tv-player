package com.baidu.tv.player.kt.model

/**
 * 播放模式。
 */
enum class PlayMode(val value: Int, val displayName: String) {
    SEQUENTIAL(0, "顺序播放"),
    RANDOM(1, "随机播放"),
    SINGLE(2, "单曲循环"),
    REVERSE(3, "倒序播放");

    companion object {
        @JvmStatic
        fun fromValue(value: Int): PlayMode =
            entries.firstOrNull { it.value == value } ?: SEQUENTIAL
    }
}
