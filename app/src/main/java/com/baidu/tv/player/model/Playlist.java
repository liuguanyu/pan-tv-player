package com.baidu.tv.player.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 播放列表实体类
 */
@Entity(tableName = "playlists")
public class Playlist {
    @PrimaryKey(autoGenerate = true)
    private long id;                    // 播放列表ID
    
    private String name;                // 播放列表名称
    private long createdAt;             // 创建时间（毫秒）
    private long lastPlayedAt;          // 最后播放时间（毫秒）
    private int lastPlayedIndex;        // 最后播放的文件索引
    private int mediaType;              // 媒体类型：0=混合, 1=视频, 2=图片
    private String coverImagePath;      // 封面图片路径
    private int totalItems;             // 总文件数
    private long totalDuration;         // 总时长（毫秒，仅视频）
    private int sortOrder;              // 排序顺序
    private String sourcePaths;         // 源目录路径列表 (JSON格式字符串)，用于刷新内容

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getLastPlayedAt() {
        return lastPlayedAt;
    }

    public void setLastPlayedAt(long lastPlayedAt) {
        this.lastPlayedAt = lastPlayedAt;
    }

    public int getLastPlayedIndex() {
        return lastPlayedIndex;
    }

    public void setLastPlayedIndex(int lastPlayedIndex) {
        this.lastPlayedIndex = lastPlayedIndex;
    }

    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public String getCoverImagePath() {
        return coverImagePath;
    }

    public void setCoverImagePath(String coverImagePath) {
        this.coverImagePath = coverImagePath;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public long getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(long totalDuration) {
        this.totalDuration = totalDuration;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getSourcePaths() {
        return sourcePaths;
    }

    public void setSourcePaths(String sourcePaths) {
        this.sourcePaths = sourcePaths;
    }
}