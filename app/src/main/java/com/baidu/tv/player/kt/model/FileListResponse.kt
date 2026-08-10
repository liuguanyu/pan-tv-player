package com.baidu.tv.player.kt.model

import com.google.gson.annotations.SerializedName

/**
 * 文件列表响应模型（百度网盘 xpan/file、xpan/multimedia 接口共用）。
 */
data class FileListResponse(
    @SerializedName("errno") val errno: Int = 0,
    @SerializedName("errmsg") val errmsg: String? = null,
    @SerializedName("list") val list: List<FileInfo>? = null,
    @SerializedName("guid_info") val guidInfo: String? = null,
    @SerializedName("request_id") val requestId: Long = 0,
    @SerializedName("has_more") val hasMore: Int = 0,
    @SerializedName("cursor") val cursor: String? = null,
) {
    /** 是否成功。 */
    fun isSuccess(): Boolean = errno == 0
}
