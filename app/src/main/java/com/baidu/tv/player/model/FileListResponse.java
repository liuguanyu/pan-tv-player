package com.baidu.tv.player.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * 文件列表响应模型
 */
public class FileListResponse {
    @SerializedName("errno")
    private int errno;
    
    private String errmsg;
    
    @SerializedName("list")
    private List<FileInfo> list;
    
    @SerializedName("guid_info")
    private String guidInfo;
    
    @SerializedName("request_id")
    private long requestId;
    
    @SerializedName("has_more")
    private int hasMore;
    
    private String cursor;

    public int getErrno() {
        return errno;
    }

    public void setErrno(int errno) {
        this.errno = errno;
    }

    public String getErrmsg() {
        return errmsg;
    }

    public void setErrmsg(String errmsg) {
        this.errmsg = errmsg;
    }

    public List<FileInfo> getList() {
        return list;
    }

    public void setList(List<FileInfo> list) {
        this.list = list;
    }

    public String getGuidInfo() {
        return guidInfo;
    }

    public void setGuidInfo(String guidInfo) {
        this.guidInfo = guidInfo;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public int getHasMore() {
        return hasMore;
    }

    public void setHasMore(int hasMore) {
        this.hasMore = hasMore;
    }

    public String getCursor() {
        return cursor;
    }

    public void setCursor(String cursor) {
        this.cursor = cursor;
    }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return errno == 0;
    }
}