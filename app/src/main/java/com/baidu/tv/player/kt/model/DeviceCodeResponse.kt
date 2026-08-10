package com.baidu.tv.player.kt.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * 设备码响应模型（百度 OAuth 设备码授权）。
 */
@Parcelize
data class DeviceCodeResponse(
    @SerializedName("device_code") val deviceCode: String? = null,
    @SerializedName("user_code") val userCode: String? = null,
    @SerializedName("verification_url") val verificationUrl: String? = null,
    @SerializedName("expires_in") val expiresIn: Long = 0,
    @SerializedName("interval") val interval: Int = 0,
    @SerializedName("error") val error: String? = null,
    @SerializedName("error_description") val errorDescription: String? = null,
) : Parcelable {
    /** 是否有错误。 */
    fun hasError(): Boolean = !error.isNullOrEmpty()

    /** 拼接带 user_code 的完整验证 URL（供 TV 端展示二维码）。 */
    val fullVerificationUrl: String?
        get() = if (verificationUrl == null || userCode == null) null
        else "$verificationUrl?code=$userCode"
}
