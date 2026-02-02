package com.baidu.tv.player.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.baidu.tv.player.model.PlaybackHistory;
import com.baidu.tv.player.model.Playlist;
import com.baidu.tv.player.model.PlaylistItem;

@Database(entities = {PlaybackHistory.class, Playlist.class, PlaylistItem.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    
    public abstract PlaybackHistoryDao playbackHistoryDao();
    public abstract PlaylistDao playlistDao();
    public abstract PlaylistItemDao playlistItemDao();
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "baidu_tv_player.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
    
    public static AppDatabase getDatabase(Context context) {
        return getInstance(context);
    }
}