package com.baidu.tv.player.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.baidu.tv.player.model.Playlist;

import java.util.List;

/**
 * 播放列表DAO
 */
@Dao
public interface PlaylistDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Playlist playlist);
    
    @Update
    void update(Playlist playlist);
    
    @Delete
    void delete(Playlist playlist);
    
    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, createdAt DESC")
    LiveData<List<Playlist>> getAllPlaylists();
    
    @Query("SELECT * FROM playlists ORDER BY sortOrder ASC, createdAt DESC")
    List<Playlist> getAllPlaylistsSync();
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    LiveData<Playlist> getPlaylistById(long id);
    
    @Query("SELECT * FROM playlists WHERE id = :id")
    Playlist getPlaylistByIdSync(long id);
    
    @Query("DELETE FROM playlists")
    void deleteAll();
}