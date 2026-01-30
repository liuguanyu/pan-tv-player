package com.baidu.tv.player.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.baidu.tv.player.database.AppDatabase;
import com.baidu.tv.player.model.PlaybackHistory;

import java.util.List;

/**
 * 主界面视图模型
 */
public class MainViewModel extends AndroidViewModel {
    
    private final AppDatabase database;
    private final LiveData<List<PlaybackHistory>> recentHistory;
    
    public MainViewModel(@NonNull Application application) {
        super(application);
        database = AppDatabase.getInstance(application);
        recentHistory = database.playbackHistoryDao().getTop4History();
    }
    
    public LiveData<List<PlaybackHistory>> getRecentHistory() {
        return recentHistory;
    }
}