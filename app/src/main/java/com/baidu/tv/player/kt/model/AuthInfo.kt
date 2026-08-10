package com.baidu.tv.player.kt.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 认证信息领域模型（内存态，不直接序列化为网络 JSON）。
 */
@Parcelize
data class AuthInfo(
    var accessToken: String? = null,
    var refreshToken: String? = null,
    var expiresAt: Long = 0,
    var scope: String? = null,
    var sessionSecret: String? = null,
    var sessionKey: String? = null,
    var sessionExpiresAt: Long = 0,
    var userId: String? = null,
    var username: String? = null,
    var isLoggedIn: Boolean = false,
) : Parcelable {
    /** 检查 token 是否过期。 */
    fun isTokenExpired(): Boolean = System.currentTimeMillis() >= expiresAt
}
