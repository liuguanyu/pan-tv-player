package com.baidu.tv.player.utils;

import com.baidu.tv.player.model.FileInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 播放列表缓存工具
 * 用于解决通过Intent传递大量数据导致TransactionTooLargeException的问题
 */
public class PlaylistCache {
    private static PlaylistCache instance;
    private final ConcurrentHashMap<String, List<FileInfo>> cache;

    private PlaylistCache() {
        cache = new ConcurrentHashMap<>();
    }

    public static synchronized PlaylistCache getInstance() {
        if (instance == null) {
            instance = new PlaylistCache();
        }
        return instance;
    }

    /**
     * 保存播放列表
     * @param key 唯一标识符（通常使用时间戳或特定ID）
     * @param playlist 播放列表
     */
    public void put(String key, List<FileInfo> playlist) {
        if (key != null && playlist != null) {
            // 创建副本以避免外部修改影响
            cache.put(key, new ArrayList<>(playlist));
        }
    }

    /**
     * 获取并移除播放列表（一次性使用）
     * @param key 唯一标识符
     * @return 播放列表，如果不存在则返回null
     */
    public List<FileInfo> getAndRemove(String key) {
        if (key == null) return null;
        return cache.remove(key);
    }
    
    /**
     * 获取播放列表（不移除）
     * @param key 唯一标识符
     * @return 播放列表，如果不存在则返回null
     */
    public List<FileInfo> get(String key) {
        if (key == null) return null;
        List<FileInfo> list = cache.get(key);
        return list != null ? new ArrayList<>(list) : null;
    }

    /**
     * 清除所有缓存
     */
    public void clear() {
        cache.clear();
    }
}