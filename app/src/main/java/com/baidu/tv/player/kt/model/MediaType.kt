package com.baidu.tv.player.kt.model

/**
 * 媒体类型枚举。code 与百度 API category / 播放列表过滤约定一致：
 * 1=图片，2=视频，3=图片+视频。
 */
enum class MediaType(val code: Int, val displayName: String) {
    IMAGE(1, "图片"),
    VIDEO(2, "视频"),
    ALL(3, "图片+视频"),
    AUDIO(4, "音频");

    /** value 与 code 同义，兼容旧调用。 */
    val value: Int get() = code

    companion object {
        @JvmStatic
        fun fromCode(code: Int): MediaType =
            entries.firstOrNull { it.code == code } ?: ALL
    }
}
