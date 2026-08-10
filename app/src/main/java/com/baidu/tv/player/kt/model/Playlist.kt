package com.baidu.tv.player.kt.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 播放列表实体（Room，表 playlists）。schema 与 Java 版 v1 保持一致。
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val createdAt: Long = 0,
    var lastPlayedAt: Long = 0,
    var lastPlayedIndex: Int = 0,
    val mediaType: Int = 0,           // 0=混合, 1=视频, 2=图片
    var coverImagePath: String? = null,
    var totalItems: Int = 0,
    var totalDuration: Long = 0,
    var sortOrder: Int = 0,
    val sourcePaths: String? = null,  // 源目录路径 JSON 数组字符串，用于刷新
)
