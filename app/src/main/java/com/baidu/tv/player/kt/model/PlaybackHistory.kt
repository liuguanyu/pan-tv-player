package com.baidu.tv.player.kt.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 播放历史记录实体（Room，表 playback_history）。
 *
 * 语义为**文件级**：每次开始播放某个文件即记录该文件（而非整个目录/播放列表）。
 * - 主页"最近播放"按 [lastPlayTime] 倒序展示，最新播放在最前。
 * - 去重键为 [filePath]：重复播放同一文件时，将其 [lastPlayTime] 更新为当前，等价于移动到队尾（最新）。
 * - 容量上限 100，超出按先进先出（FIFO）淘汰最旧记录（见 PlaybackHistoryRepository）。
 * - 点击后需要能"从该文件开始播放"，因此记录来源上下文：
 *   [sourcePlaylistId]（来自数据库播放列表）或 [sourceFolderPath]+[mediaType]（来自云盘目录），
 *   并用 [fsId] 在重建的列表中定位起始索引。
 */
@Entity(tableName = "playback_history")
data class PlaybackHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 文件完整路径，作为去重键。历史遗留列名沿用 folderPath。 */
    val folderPath: String = "",
    /** 文件展示名（server_filename）。历史遗留列名沿用 folderName。 */
    val folderName: String? = null,
    val mediaType: Int = 0,   // 1=图片, 2=视频, 3=混合
    val fileCount: Int = 0,
    var lastPlayTime: Long = 0,
    val createTime: Long = 0,
    /** 该文件的缩略图（thumbs.url1）或本地路径，用于主页"最近播放"卡片展示，可空。 */
    val coverImagePath: String? = null,
    /** 来源数据库播放列表 id：非空表示点击后按该列表重建上下文并定位到本文件。 */
    val sourcePlaylistId: Long? = null,
    /** 来源云盘目录路径：非空表示点击后按该目录重新拉取文件列表并定位到本文件。 */
    val sourceFolderPath: String? = null,
    /** 文件 fs_id：点击历史时用于在重建的文件列表中定位起始播放索引。 */
    val fsId: Long = 0,
)
