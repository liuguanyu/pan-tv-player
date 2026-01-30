package com.baidu.tv.player.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.baidu.tv.player.model.PlaybackHistory;

import java.util.List;

/**
 * 播放历史记录DAO
 */
@Dao
public interface PlaybackHistoryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(PlaybackHistory history);
    
    @Update
    void update(PlaybackHistory history);
    
    @Delete
    void delete(PlaybackHistory history);
    
    @Query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC")
    LiveData<List<PlaybackHistory>> getAllHistory();

    @Query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC")
    List<PlaybackHistory> getAllHistorySync();

    @Query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC LIMIT :limit")
    LiveData<List<PlaybackHistory>> getRecentHistory(int limit);
    
    @Query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC LIMIT 4")
    LiveData<List<PlaybackHistory>> getTop4History();
    
    @Query("SELECT * FROM playback_history WHERE id = :id")
    LiveData<PlaybackHistory> getHistoryById(long id);
    
    @Query("SELECT * FROM playback_history WHERE folderPath = :path LIMIT 1")
    PlaybackHistory getHistoryByPath(String path);
    
    @Query("DELETE FROM playback_history")
    void deleteAll();
}