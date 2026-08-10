package com.baidu.tv.player.kt.model

import com.google.gson.annotations.SerializedName

/**
 * Token 响应模型（设备码换 token / 刷新 token 共用）。
 */
data class TokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0,
    @SerializedName("scope") val scope: String? = null,
    @SerializedName("session_key") val sessionKey: String? = null,
    @SerializedName("session_secret") val sessionSecret: String? = null,
    @SerializedName("error") val error: String? = null,
    @SerializedName("error_description") val errorDescription: String? = null,
) {
    /** 是否有错误。 */
    fun hasError(): Boolean = !error.isNullOrEmpty()
}
