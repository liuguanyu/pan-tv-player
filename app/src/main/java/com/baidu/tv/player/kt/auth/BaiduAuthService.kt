package com.baidu.tv.player.kt.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.baidu.tv.player.kt.config.BaiduConfig
import com.baidu.tv.player.kt.di.AuthPrefs
import com.baidu.tv.player.kt.di.OAuthApi
import com.baidu.tv.player.kt.model.AuthInfo
import com.baidu.tv.player.kt.model.DeviceCodeResponse
import com.baidu.tv.player.kt.model.TokenResponse
import com.baidu.tv.player.kt.network.ApiConstants
import com.baidu.tv.player.kt.network.BaiduPanService
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BaiduAuthService"

/**
 * 百度网盘认证服务。
 *
 * - 由 Hilt 注入，替代 Java 版手动单例。
 * - Token 读写使用 [EncryptedSharedPreferences]（AES-256 GCM）。
 * - 全部为 `suspend` 函数，协程 `delay` 替代 Handler.postDelayed 轮询。
 *
 * 设备码授权轮询：百度在 pending 状态返回 HTTP 400 + `{"error":"authorization_pending"}`，
 * 成功返回 200 + token JSON。suspend Retrofit 在 4xx 抛 [HttpException]，需解析 errorBody。
 */
@Singleton
class BaiduAuthService @Inject constructor(
    @ApplicationContext private val context: Context,
    @OAuthApi private val oauthService: BaiduPanService,
    @AuthPrefs private val prefs: SharedPreferences,
) {
    private var authInfo: AuthInfo = loadAuthInfo()

    /** OAuth 授权状态（轮询用）。 */
    enum class PollResult { AUTHENTICATED, PENDING, EXPIRED, ERROR }

    /** 检查是否已认证。 */
    fun isAuthenticated(): Boolean {
        if (!authInfo.isLoggedIn) return false
        if (authInfo.accessToken.isNullOrEmpty()) return false
        if (System.currentTimeMillis() >= authInfo.expiresAt) return false
        return true
    }

    /** 获取访问令牌（仅返回当前仍在有效期内的 token）。 */
    fun getAccessToken(): String? = authInfo.accessToken?.takeIf { isAuthenticated() }

    /** 获取认证信息。 */
    fun getAuthInfo(): AuthInfo = authInfo

    /** 获取或生成设备 ID（持久化）。 */
    fun getDeviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    /** 获取设备码。失败时抛异常。 */
    suspend fun getDeviceCode(): DeviceCodeResponse {
        val resp = oauthService.getDeviceCode(BaiduConfig.APP_KEY, BaiduConfig.SCOPE, "device_code")
        if (resp.hasError()) throw IllegalStateException(resp.error)
        return resp
    }

    /**
     * 轮询设备码状态。成功保存 token 并返回 [PollResult.AUTHENTICATED]；
     * pending 返回 [PollResult.PENDING]（调用方按 [ApiConstants.POLLING_INTERVAL] 间隔重试）；
     * 过期返回 [PollResult.EXPIRED]；其他错误返回 [PollResult.ERROR]。
     */
    suspend fun pollDeviceCodeStatus(deviceCode: String): PollResult {
        return try {
            val token = oauthService.getTokenByDeviceCode(
                "device_token", deviceCode, BaiduConfig.APP_KEY, BaiduConfig.SECRET_KEY,
            )
            if (!token.accessToken.isNullOrEmpty()) {
                applyToken(token)
                PollResult.AUTHENTICATED
            } else if (token.error == "authorization_pending") {
                PollResult.PENDING
            } else if (token.error == "expired_token") {
                PollResult.EXPIRED
            } else {
                Log.e(TAG, "轮询返回错误: ${token.error} - ${token.errorDescription}")
                PollResult.ERROR
            }
        } catch (e: HttpException) {
            // 4xx：解析 errorBody 中的 error 字段
            val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
            val error = parseError(errorBody)
            when (error) {
                "authorization_pending" -> PollResult.PENDING
                "expired_token" -> PollResult.EXPIRED
                "slow_down" -> { delay(ApiConstants.POLLING_INTERVAL); PollResult.PENDING }
                else -> {
                    Log.e(TAG, "轮询 HTTP 错误: ${e.code()}, body=$errorBody")
                    PollResult.ERROR
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "轮询网络错误", e)
            PollResult.ERROR
        }
    }

    /**
     * 完整的设备码轮询循环：直到认证成功 / 过期 / 出错 / 达到最大次数。
     * @return 终态 [PollResult]：[PollResult.AUTHENTICATED] 成功；
     * [PollResult.EXPIRED] 设备码过期（含轮询次数耗尽，调用方可刷新二维码重试）；
     * [PollResult.ERROR] 不可恢复错误。
     */
    suspend fun pollUntilAuthenticated(deviceCode: String): PollResult {
        repeat(ApiConstants.MAX_POLLING_COUNT) {
            when (val result = pollDeviceCodeStatus(deviceCode)) {
                PollResult.PENDING -> delay(ApiConstants.POLLING_INTERVAL)
                else -> return result
            }
        }
        // 轮询次数耗尽视为设备码过期，由调用方刷新二维码
        return PollResult.EXPIRED
    }

    /** 刷新 token。成功返回 true。 */
    suspend fun refreshToken(): Boolean {
        if (authInfo.refreshToken.isNullOrEmpty()) return false
        return try {
            val token = oauthService.refreshToken(
                "refresh_token", authInfo.refreshToken!!, BaiduConfig.APP_KEY, BaiduConfig.SECRET_KEY,
            )
            if (!token.accessToken.isNullOrEmpty()) {
                applyToken(token)
                true
            } else {
                Log.e(TAG, "刷新令牌失败: ${token.errorDescription}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "刷新令牌异常", e)
            false
        }
    }

    /** 撤销 token（登出，服务端）。 */
    suspend fun revokeToken() {
        try {
            authInfo.accessToken?.let { oauthService.revokeToken(it) }
        } catch (e: Exception) {
            Log.w(TAG, "撤销 token 失败（忽略）", e)
        }
    }

    /** 退出登录：清空内存与持久化认证信息。 */
    fun logout() {
        authInfo = AuthInfo()
        saveAuthInfo()
    }

    /**
     * 迁移专用入口：用已读取的旧明文 [AuthInfo] 覆盖内存态并持久化到加密存储。
     * 仅供 [AuthMigration] 一次性迁移使用。
     */
    fun applyMigratedAuthInfo(info: AuthInfo) {
        authInfo = info
        saveAuthInfo()
    }

    /** 将 [TokenResponse] 写入内存 authInfo 并持久化。 */
    private fun applyToken(token: TokenResponse) {
        authInfo.accessToken = token.accessToken
        authInfo.refreshToken = token.refreshToken
        authInfo.expiresAt = System.currentTimeMillis() + token.expiresIn * 1000L
        authInfo.scope = token.scope
        authInfo.sessionKey = token.sessionKey
        authInfo.sessionSecret = token.sessionSecret
        authInfo.isLoggedIn = true
        saveAuthInfo()
    }

    private fun loadAuthInfo(): AuthInfo = AuthInfo().apply {
        accessToken = prefs.getString(KEY_ACCESS_TOKEN, "")
        refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "")
        expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0)
        scope = prefs.getString(KEY_SCOPE, "")
        sessionKey = prefs.getString(KEY_SESSION_KEY, "")
        sessionSecret = prefs.getString(KEY_SESSION_SECRET, "")
        sessionExpiresAt = prefs.getLong(KEY_SESSION_EXPIRES_AT, 0)
        userId = prefs.getString(KEY_USER_ID, "")
        username = prefs.getString(KEY_USERNAME, "")
        isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    private fun saveAuthInfo() {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, authInfo.accessToken)
            putString(KEY_REFRESH_TOKEN, authInfo.refreshToken)
            putLong(KEY_EXPIRES_AT, authInfo.expiresAt)
            putString(KEY_SCOPE, authInfo.scope)
            putString(KEY_SESSION_KEY, authInfo.sessionKey)
            putString(KEY_SESSION_SECRET, authInfo.sessionSecret)
            putLong(KEY_SESSION_EXPIRES_AT, authInfo.sessionExpiresAt)
            putString(KEY_USER_ID, authInfo.userId)
            putString(KEY_USERNAME, authInfo.username)
            putBoolean(KEY_IS_LOGGED_IN, authInfo.isLoggedIn)
        }.apply()
    }

    /** 从 errorBody JSON 解析 `error` 字段。 */
    private fun parseError(errorBody: String?): String? = try {
        if (errorBody.isNullOrEmpty()) null
        else Gson().fromJson(errorBody, TokenResponse::class.java).error
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_SCOPE = "scope"
        private const val KEY_SESSION_KEY = "session_key"
        private const val KEY_SESSION_SECRET = "session_secret"
        private const val KEY_SESSION_EXPIRES_AT = "session_expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_DEVICE_ID = "device_id"

        /** 旧明文 SharedPreferences 文件名（用于一次性迁移检测）。 */
        const val LEGACY_PLAIN_PREF_NAME = "baidu_aid"
    }
}
