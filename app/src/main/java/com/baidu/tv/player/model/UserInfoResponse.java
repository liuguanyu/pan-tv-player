package com.baidu.tv.player.model;

import com.google.gson.annotations.SerializedName;

/**
 * 用户信息响应模型
 */
public class UserInfoResponse {
    
    @SerializedName("errno")
    private int errno;
    
    @SerializedName("errmsg")
    private String errmsg;
    
    @SerializedName("baidu_name")
    private String baiduName;
    
    @SerializedName("netdisk_name")
    private String netdiskName;
    
    @SerializedName("avatar_url")
    private String avatarUrl;
    
    @SerializedName("vip_type")
    private int vipType;
    
    @SerializedName("uk")
    private long uk;

    // Getters and Setters
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

    public String getBaiduName() {
        return baiduName;
    }

    public void setBaiduName(String baiduName) {
        this.baiduName = baiduName;
    }

    public String getNetdiskName() {
        return netdiskName;
    }

    public void setNetdiskName(String netdiskName) {
        this.netdiskName = netdiskName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public int getVipType() {
        return vipType;
    }

    public void setVipType(int vipType) {
        this.vipType = vipType;
    }

    public long getUk() {
        return uk;
    }

    public void setUk(long uk) {
        this.uk = uk;
    }

    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return errno == 0;
    }
}