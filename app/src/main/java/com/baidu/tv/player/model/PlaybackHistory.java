package com.baidu.tv.player.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 播放历史记录实体
 */
@Entity(tableName = "playback_history")
public class PlaybackHistory {
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    private String folderPath;
    private String folderName;
    private int mediaType; // 1: 图片, 2: 视频, 3: 混合
    private int fileCount;
    private long lastPlayTime;
    private long createTime;

    public PlaybackHistory() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public long getLastPlayTime() {
        return lastPlayTime;
    }

    public void setLastPlayTime(long lastPlayTime) {
        this.lastPlayTime = lastPlayTime;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
}