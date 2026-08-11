package com.baidu.tv.player.kt.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baidu.tv.player.kt.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow

/**
 * 播放历史记录 DAO。
 */
@Dao
interface PlaybackHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: PlaybackHistory): Long

    @Update
    suspend fun update(history: PlaybackHistory)

    @Delete
    suspend fun delete(history: PlaybackHistory)

    @Query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC, id DESC")
    fun getAllHistory(): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC, id DESC")
    suspend fun getAllHistorySync(): List<PlaybackHistory>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC, id DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC, id DESC LIMIT 4")
    fun getTop4History(): Flow<List<PlaybackHistory>>

    @Query("SELECT * FROM playback_history WHERE id = :id")
    fun getHistoryById(id: Long): Flow<PlaybackHistory?>

    @Query("SELECT * FROM playback_history WHERE folderPath = :path LIMIT 1")
    suspend fun getHistoryByPath(path: String): PlaybackHistory?

    @Query("UPDATE playback_history SET coverImagePath = :coverPath WHERE folderPath = :filePath")
    suspend fun updateCoverPath(filePath: String, coverPath: String)

    /** 历史记录总数，用于判断是否超出容量上限。 */
    @Query("SELECT COUNT(*) FROM playback_history")
    suspend fun count(): Int

    /**
     * 先进先出裁剪：仅保留最近 [keep] 条（按 lastPlayTime 倒序），删除更旧的记录。
     * 通过子查询选出需保留的 id 集合，删除其余记录。
     */
    @Query(
        "DELETE FROM playback_history WHERE id NOT IN (" +
            "SELECT id FROM playback_history ORDER BY lastPlayTime DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimToLimit(keep: Int)

    @Query("DELETE FROM playback_history")
    suspend fun deleteAll()
}
