package com.baidu.tv.player.kt.auth

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthMigration"

/**
 * 一次性迁移：检测旧明文 SharedPreferences（`baidu_aid`）中的 token，
 * 迁移到 [BaiduAuthService] 的 EncryptedSharedPreferences，迁移成功后删除旧文件。
 *
 * 在 [BaiduAuthService] 首次构建前调用（由 [com.baidu.tv.player.kt.BaiduTVApplication]
 * 启动时触发）。无旧数据时静默跳过。
 */
@Singleton
class AuthMigration @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authService: BaiduAuthService,
) {
    /**
     * 执行迁移。返回 true 表示发生了迁移。
     * 读取旧明文 key（access_token / refresh_token / expires_at 等）→ 写入加密存储 → 删旧文件。
     */
    fun migrate(): Boolean {
        val legacyPrefs = try {
            context.getSharedPreferences(BaiduAuthService.LEGACY_PLAIN_PREF_NAME, Context.MODE_PRIVATE)
        } catch (e: Exception) {
            Log.w(TAG, "读取旧明文 SharedPreferences 失败", e)
            return false
        }

        val accessToken = legacyPrefs.getString(KEY_ACCESS_TOKEN, null)
        if (accessToken.isNullOrEmpty()) {
            // 无旧数据，静默跳过
            return false
        }

        Log.i(TAG, "检测到旧明文 token，开始迁移到 EncryptedSharedPreferences")
        val info = authService.getAuthInfo()
        info.accessToken = accessToken
        info.refreshToken = legacyPrefs.getString(KEY_REFRESH_TOKEN, null)
        info.expiresAt = legacyPrefs.getLong(KEY_EXPIRES_AT, 0)
        info.scope = legacyPrefs.getString(KEY_SCOPE, null)
        info.sessionKey = legacyPrefs.getString(KEY_SESSION_KEY, null)
        info.sessionSecret = legacyPrefs.getString(KEY_SESSION_SECRET, null)
        info.sessionExpiresAt = legacyPrefs.getLong(KEY_SESSION_EXPIRES_AT, 0)
        info.userId = legacyPrefs.getString(KEY_USER_ID, null)
        info.username = legacyPrefs.getString(KEY_USERNAME, null)
        info.isLoggedIn = true

        // 触发持久化：复用 BaiduAuthService 的保存路径。
        // BaiduAuthService 内部 saveAuthInfo 是 private，这里通过 logout+重设+触发保存不可行；
        // 改为暴露一个迁移写入入口。
        authService.applyMigratedAuthInfo(info)

        // 删除旧明文文件：先 clear 再尝试删除文件。deleteSharedPreferences 为 API 24+，
        // 旧设备/部分测试环境可能抛 NoSuchMethodError（Error，非 Exception），故捕 Throwable。
        try {
            legacyPrefs.edit().clear().apply()
            // 注意：SharedPreferences 文件删除需通过 deleteSharedPreferences（API 24+）
            context.deleteSharedPreferences(BaiduAuthService.LEGACY_PLAIN_PREF_NAME)
            Log.i(TAG, "旧明文 token 迁移完成，已删除旧文件")
        } catch (e: Throwable) {
            // 即使文件未删除，数据已 clear，token 不再可读
            Log.w(TAG, "删除旧明文 SharedPreferences 文件失败（数据已 clear）", e)
        }
        return true
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
    }
}
