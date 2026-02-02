package com.baidu.tv.player.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.baidu.tv.player.database.AppDatabase;
import com.baidu.tv.player.database.PlaylistDao;
import com.baidu.tv.player.database.PlaylistItemDao;
import com.baidu.tv.player.model.Playlist;
import com.baidu.tv.player.model.PlaylistItem;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 播放列表数据仓库
 */
public class PlaylistRepository {
    private static final String TAG = "PlaylistRepository";
    
    private final PlaylistDao playlistDao;
    private final PlaylistItemDao playlistItemDao;
    private final ExecutorService executorService;
    
    public PlaylistRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        playlistDao = database.playlistDao();
        playlistItemDao = database.playlistItemDao();
        executorService = Executors.newFixedThreadPool(4);
    }
    
    /**
     * 获取所有播放列表
     */
    public LiveData<List<Playlist>> getAllPlaylists() {
        return playlistDao.getAllPlaylists();
    }
    
    /**
     * 同步获取所有播放列表
     */
    public List<Playlist> getAllPlaylistsSync() {
        return playlistDao.getAllPlaylistsSync();
    }
    
    /**
     * 根据ID获取播放列表
     */
    public LiveData<Playlist> getPlaylistById(long id) {
        return playlistDao.getPlaylistById(id);
    }
    
    /**
     * 同步根据ID获取播放列表
     */
    public Playlist getPlaylistByIdSync(long id) {
        return playlistDao.getPlaylistByIdSync(id);
    }
    
    /**
     * 插入播放列表
     */
    public interface InsertCallback {
        void onSuccess(long id);
        void onError(Exception e);
    }

    public void insertPlaylist(Playlist playlist, InsertCallback callback) {
        executorService.execute(() -> {
            try {
                long id = playlistDao.insert(playlist);
                Log.d(TAG, "播放列表插入成功, ID: " + id);
                if (callback != null) {
                    // 在主线程回调
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onSuccess(id));
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表插入失败", e);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(e));
                }
            }
        });
    }
    
    /**
     * 更新播放列表
     */
    public void updatePlaylist(Playlist playlist, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                playlistDao.update(playlist);
                Log.d(TAG, "播放列表更新成功, ID: " + playlist.getId());
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表更新失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
    
    /**
     * 删除播放列表
     */
    public void deletePlaylist(Playlist playlist, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                playlistDao.delete(playlist);
                Log.d(TAG, "播放列表删除成功, ID: " + playlist.getId());
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表删除失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
    
    /**
     * 获取播放列表的所有项
     */
    public LiveData<List<PlaylistItem>> getPlaylistItems(long playlistId) {
        return playlistItemDao.getItemsByPlaylistId(playlistId);
    }
    
    /**
     * 同步获取播放列表的所有项
     */
    public List<PlaylistItem> getPlaylistItemsSync(long playlistId) {
        return playlistItemDao.getItemsByPlaylistIdSync(playlistId);
    }
    
    /**
     * 插入播放列表项
     */
    public void insertPlaylistItems(List<PlaylistItem> items, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                playlistItemDao.insertAll(items);
                Log.d(TAG, "播放列表项插入成功, 数量: " + items.size());
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表项插入失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
    
    /**
     * 删除播放列表的所有项
     */
    public void deletePlaylistItems(long playlistId, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                playlistItemDao.deleteByPlaylistId(playlistId);
                Log.d(TAG, "播放列表项删除成功, playlistId: " + playlistId);
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "播放列表项删除失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
    
    /**
     * 获取播放列表项数量
     */
    public int getPlaylistItemCount(long playlistId) {
        return playlistItemDao.getItemCount(playlistId);
    }
}