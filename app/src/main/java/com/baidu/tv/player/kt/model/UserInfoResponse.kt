package com.baidu.tv.player.kt.model

import com.google.gson.annotations.SerializedName

/**
 * 用户信息响应模型（百度网盘 NAS 接口）。
 */
data class UserInfoResponse(
    @SerializedName("errno") val errno: Int = 0,
    @SerializedName("errmsg") val errmsg: String? = null,
    @SerializedName("baidu_name") val baiduName: String? = null,
    @SerializedName("netdisk_name") val netdiskName: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("vip_type") val vipType: Int = 0,
    @SerializedName("uk") val uk: Long = 0,
) {
    /** 是否成功。 */
    fun isSuccess(): Boolean = errno == 0
}
