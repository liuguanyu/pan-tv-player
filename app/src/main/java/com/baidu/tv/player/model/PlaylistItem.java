package com.baidu.tv.player.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 播放列表项实体类
 */
@Entity(
    tableName = "playlist_items",
    foreignKeys = @ForeignKey(
        entity = Playlist.class,
        parentColumns = "id",
        childColumns = "playlistId",
        onDelete = ForeignKey.CASCADE  // 删除播放列表时级联删除所有项
    ),
    indices = {@Index("playlistId")}   // 为playlistId建立索引提高查询速度
)
public class PlaylistItem {
    @PrimaryKey(autoGenerate = true)
    private long id;                    // 播放列表项ID
    
    private long playlistId;            // 所属播放列表ID
    private long fsId;                  // 百度网盘文件fsId（用于获取dlink）
    private String filePath;            // 文件路径（相对路径）
    private String fileName;            // 文件名
    private int mediaType;              // 媒体类型：1=视频, 2=图片
    private int sortOrder;              // 排序顺序
    private long duration;              // 时长（毫秒，仅视频）
    private long fileSize;              // 文件大小（字节）

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(long playlistId) {
        this.playlistId = playlistId;
    }

    public long getFsId() {
        return fsId;
    }

    public void setFsId(long fsId) {
        this.fsId = fsId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
}