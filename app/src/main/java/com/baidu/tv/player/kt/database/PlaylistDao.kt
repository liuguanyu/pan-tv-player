package com.baidu.tv.player.kt.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.baidu.tv.player.kt.model.Playlist
import kotlinx.coroutines.flow.Flow

/**
 * 播放列表 DAO。可观察查询返回 Flow（替代 Java 版 LiveData），
 * 一次性查询与写操作使用 suspend 函数（替代 ExecutorService + Handler）。
 */
@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: Playlist): Long

    @Update
    suspend fun update(playlist: Playlist)

    @Delete
    suspend fun delete(playlist: Playlist)

    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAllPlaylistsSync(): List<Playlist>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun getPlaylistById(id: Long): Flow<Playlist?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistByIdSync(id: Long): Playlist?

    @Query("DELETE FROM playlists")
    suspend fun deleteAll()
}
