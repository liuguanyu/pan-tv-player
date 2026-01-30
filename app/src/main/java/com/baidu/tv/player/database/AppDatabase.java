package com.baidu.tv.player.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.baidu.tv.player.model.PlaybackHistory;

@Database(entities = {PlaybackHistory.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    
    private static volatile AppDatabase INSTANCE;
    
    public abstract PlaybackHistoryDao playbackHistoryDao();
    
    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "baidu_tv_player.db")
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