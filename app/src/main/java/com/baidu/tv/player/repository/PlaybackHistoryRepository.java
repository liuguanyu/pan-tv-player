package com.baidu.tv.player.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.baidu.tv.player.database.AppDatabase;
import com.baidu.tv.player.database.PlaybackHistoryDao;
import com.baidu.tv.player.model.PlaybackHistory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 播放历史记录数据仓库
 */
public class PlaybackHistoryRepository {
    private PlaybackHistoryDao historyDao;
    private LiveData<List<PlaybackHistory>> allHistory;
    private ExecutorService executor;

    public PlaybackHistoryRepository(Application application) {
        AppDatabase db = AppDatabase.getDatabase(application);
        historyDao = db.playbackHistoryDao();
        allHistory = historyDao.getAllHistory();
        executor = Executors.newFixedThreadPool(2);
    }

    /**
     * 获取所有历史记录
     */
    public LiveData<List<PlaybackHistory>> getAllHistory() {
        return allHistory;
    }

    /**
     * 获取最近的历史记录（限制数量）
     */
    public LiveData<List<PlaybackHistory>> getRecentHistory(int limit) {
        return historyDao.getRecentHistory(limit);
    }

    /**
     * 根据ID获取历史记录
     */
    public LiveData<PlaybackHistory> getHistoryById(long id) {
        return historyDao.getHistoryById(id);
    }

    /**
     * 插入历史记录
     */
    public void insert(PlaybackHistory history) {
        executor.execute(() -> {
            // 检查是否已存在相同的历史记录
            PlaybackHistory existing = historyDao.getHistoryByPath(history.getFolderPath());
            if (existing != null) {
                // 更新现有记录的时间
                existing.setLastPlayTime(System.currentTimeMillis());
                historyDao.update(existing);
            } else {
                // 插入新记录
                history.setLastPlayTime(System.currentTimeMillis());
                historyDao.insert(history);
            }
        });
    }

    /**
     * 删除历史记录
     */
    public void delete(PlaybackHistory history) {
        executor.execute(() -> historyDao.delete(history));
    }

    /**
     * 删除所有历史记录
     */
    public void deleteAll() {
        executor.execute(() -> historyDao.deleteAll());
    }
}