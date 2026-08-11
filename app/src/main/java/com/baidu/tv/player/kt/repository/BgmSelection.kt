package com.baidu.tv.player.kt.repository

/** 用户选择的网盘背景音乐文件，不保存音频内容，仅保存定位信息。 */
data class BgmSelection(
    val fsId: Long,
    val path: String?,
    val name: String?,
)
