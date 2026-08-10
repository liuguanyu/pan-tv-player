package com.baidu.tv.player.kt.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 播放列表项实体（Room，表 playlist_items）。删除播放列表时级联删除。
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long = 0,
    val fsId: Long = 0,
    val filePath: String? = null,
    val fileName: String? = null,
    val mediaType: Int = 0,   // 1=视频, 2=图片
    val sortOrder: Int = 0,
    val duration: Long = 0,
    val fileSize: Long = 0,
)
