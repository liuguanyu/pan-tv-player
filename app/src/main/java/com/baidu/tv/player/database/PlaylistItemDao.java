package com.baidu.tv.player.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.baidu.tv.player.model.PlaylistItem;

import java.util.List;

/**
 * 播放列表项DAO
 */
@Dao
public interface PlaylistItemDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PlaylistItem item);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<PlaylistItem> items);
    
    @Delete
    void delete(PlaylistItem item);
    
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    LiveData<List<PlaylistItem>> getItemsByPlaylistId(long playlistId);
    
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY sortOrder ASC")
    List<PlaylistItem> getItemsByPlaylistIdSync(long playlistId);
    
    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    void deleteByPlaylistId(long playlistId);
    
    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    int getItemCount(long playlistId);
}