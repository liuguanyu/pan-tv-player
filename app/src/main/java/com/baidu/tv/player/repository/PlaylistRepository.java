package com.baidu.tv.player.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.baidu.tv.player.auth.BaiduAuthService;
import com.baidu.tv.player.database.AppDatabase;
import com.baidu.tv.player.database.PlaylistDao;
import com.baidu.tv.player.database.PlaylistItemDao;
import com.baidu.tv.player.model.Playlist;
import com.baidu.tv.player.model.PlaylistItem;
import com.baidu.tv.player.model.FileInfo;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;

/**
 * 播放列表数据仓库
 */
public class PlaylistRepository {
    private static final String TAG = "PlaylistRepository";
    
    private final Context context;
    private final PlaylistDao playlistDao;
    private final PlaylistItemDao playlistItemDao;
    private final ExecutorService executorService;
    
    public PlaylistRepository(Context context) {
        this.context = context.getApplicationContext();
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

    /**
     * 刷新播放列表
     * @param playlist 要刷新的播放列表
     * @param onSuccess 成功回调
     * @param onError 失败回调
     */
    public void refreshPlaylist(Playlist playlist, Runnable onSuccess, Runnable onError) {
        executorService.execute(() -> {
            try {
                Log.d(TAG, "开始刷新播放列表: " + playlist.getName());
                
                // 获取访问令牌
                String accessToken = BaiduAuthService.getInstance(context).getAccessToken();
                if (accessToken == null || accessToken.isEmpty()) {
                    Log.e(TAG, "未获取到访问令牌，请先登录");
                    if (onError != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                    }
                    return;
                }
                
                // 1. 获取源目录路径
                String sourcePathsJson = playlist.getSourcePaths();
                if (sourcePathsJson == null || sourcePathsJson.isEmpty()) {
                    Log.w(TAG, "播放列表没有源目录信息，无法刷新");
                    if (onError != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                    }
                    return;
                }
                
                // 解析源目录路径
                List<String> sourcePaths = new java.util.ArrayList<>();
                try {
                    org.json.JSONArray jsonArray = new org.json.JSONArray(sourcePathsJson);
                    for (int i = 0; i < jsonArray.length(); i++) {
                        sourcePaths.add(jsonArray.getString(i));
                    }
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "解析源目录路径失败", e);
                    if (onError != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                    }
                    return;
                }
                
                if (sourcePaths.isEmpty()) {
                    Log.w(TAG, "播放列表源目录为空，无法刷新");
                    if (onError != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                    }
                    return;
                }
                
                // 2. 递归获取所有文件
                List<com.baidu.tv.player.model.FileInfo> allFiles = new java.util.ArrayList<>();
                final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(sourcePaths.size());
                final java.util.concurrent.atomic.AtomicBoolean hasError = new java.util.concurrent.atomic.AtomicBoolean(false);
                
                for (String path : sourcePaths) {
                    FileRepository.getInstance().fetchFilesRecursive(accessToken, path, new FileRepository.FileListCallback() {
                        @Override
                        public void onSuccess(List<com.baidu.tv.player.model.FileInfo> files) {
                            synchronized (allFiles) {
                                allFiles.addAll(files);
                            }
                            latch.countDown();
                        }

                        @Override
                        public void onFailure(String error) {
                            Log.e(TAG, "获取目录文件失败: " + path + ", error: " + error);
                            // 即使某个目录失败，也尝试继续处理其他目录
                            latch.countDown();
                        }
                    });
                }
                
                try {
                    // 等待所有文件获取完成，最长等待5分钟
                    latch.await(5, java.util.concurrent.TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Log.e(TAG, "等待文件获取被中断", e);
                    hasError.set(true);
                }
                
                if (hasError.get()) {
                    if (onError != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                    }
                    return;
                }
                
                Log.d(TAG, "获取到新文件列表，总数: " + allFiles.size());
                
                // 3. 过滤并转换为播放列表项
                List<PlaylistItem> newItems = new java.util.ArrayList<>();
                long totalDuration = 0;
                int videoCount = 0;
                int imageCount = 0;
                
                // 检查播放列表类型
                int targetMediaType = playlist.getMediaType();
                
                for (int i = 0; i < allFiles.size(); i++) {
                    com.baidu.tv.player.model.FileInfo fileInfo = allFiles.get(i);
                    
                    // 根据播放列表类型过滤
                    boolean shouldAdd = false;
                    if (targetMediaType == 0) { // 混合
                        shouldAdd = fileInfo.isVideo() || fileInfo.isImage();
                    } else if (targetMediaType == 1) { // 视频
                        shouldAdd = fileInfo.isVideo();
                    } else if (targetMediaType == 2) { // 图片
                        shouldAdd = fileInfo.isImage();
                    }
                    
                    if (shouldAdd) {
                        PlaylistItem item = new PlaylistItem();
                        item.setPlaylistId(playlist.getId());
                        item.setFsId(fileInfo.getFsId());
                        item.setFilePath(fileInfo.getPath());
                        item.setFileName(fileInfo.getServerFilename());
                        item.setFileSize(fileInfo.getSize());
                        item.setSortOrder(i);
                        
                        if (fileInfo.isVideo()) {
                            item.setMediaType(1);
                            // 注意：FileInfo中可能没有时长信息，这里暂时设为0
                            // 实际应用中可能需要额外获取视频详情
                            item.setDuration(0);
                            videoCount++;
                        } else if (fileInfo.isImage()) {
                            item.setMediaType(2);
                            imageCount++;
                        }
                        
                        newItems.add(item);
                    }
                }
                
                // 4. 更新数据库
                // 使用事务操作：删除旧项，插入新项，更新播放列表信息
                AppDatabase.getInstance(null).runInTransaction(() -> {
                    // 删除旧项
                    playlistItemDao.deleteByPlaylistId(playlist.getId());
                    
                    // 插入新项
                    playlistItemDao.insertAll(newItems);
                    
                    // 更新播放列表统计信息
                    playlist.setTotalItems(newItems.size());
                    // 如果全是图片，时长为0；如果有视频，这里仅累加已知时长
                    // 由于列表接口可能不返回时长，这里暂不更新时长，或者保持原有时长逻辑
                    
                    // 更新封面：如果原封面是其中的文件，保留；否则使用第一个文件的缩略图
                    if (!newItems.isEmpty()) {
                        String firstItemThumb = newItems.get(0).getFilePath(); // 这里应该使用缩略图地址，但暂时用路径代替
                        // 实际上应该保持原封面，或者提供选项更新封面
                        // 这里简单处理：如果当前没有封面，设置第一个项目的封面
                        if (playlist.getCoverImagePath() == null || playlist.getCoverImagePath().isEmpty()) {
                            // 注意：这里只是路径，实际显示时需要获取缩略图链接
                            // 我们可以暂时不更新封面，或者需要额外的逻辑来获取缩略图
                        }
                    }
                    
                    playlistDao.update(playlist);
                });
                
                Log.d(TAG, "播放列表刷新完成，新文件数: " + newItems.size());
                
                if (onSuccess != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onSuccess);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "刷新播放列表失败", e);
                if (onError != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(onError);
                }
            }
        });
    }
}