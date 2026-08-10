package com.baidu.tv.player.kt.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baidu.tv.player.kt.network.FakeBaiduPanService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * AuthMigration 单测：旧明文 SharedPreferences → 加密（此处用明文 prefs 替身）一次性迁移。
 */
@RunWith(RobolectricTestRunner::class)
class AuthMigrationTest {

    private lateinit var context: Context
    private lateinit var fake: FakeBaiduPanService
    private lateinit var service: BaiduAuthService
    private lateinit var migration: AuthMigration

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 清理可能的残留（deleteSharedPreferences 在部分 Robolectric 版本不可用，用 clear 兜底）
        clearPrefs(BaiduAuthService.LEGACY_PLAIN_PREF_NAME)
        clearPrefs("test_auth_prefs")
        fake = FakeBaiduPanService()
        service = BaiduAuthService(context, fake, context.getSharedPreferences("test_auth_prefs", Context.MODE_PRIVATE))
        migration = AuthMigration(context, service)
    }

    private fun clearPrefs(name: String) {
        try {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        } catch (_: Throwable) {
        }
        try {
            context.deleteSharedPreferences(name)
        } catch (_: Throwable) {
        }
    }

    @Test
    fun migrate_skipsSilently_whenNoLegacyData() = runTest {
        assertFalse(migration.migrate())
        assertFalse(service.isAuthenticated())
    }

    @Test
    fun migrate_transfersLegacyToken_andDeletesOldFile() = runTest {
        // 准备旧明文 token
        val legacy = context.getSharedPreferences(BaiduAuthService.LEGACY_PLAIN_PREF_NAME, Context.MODE_PRIVATE)
        legacy.edit()
            .putString("access_token", "legacy-at")
            .putString("refresh_token", "legacy-rt")
            .putLong("expires_at", System.currentTimeMillis() + 3_600_000L)
            .putString("scope", "basic,netdisk")
            .putString("username", "tester")
            .apply()

        assertTrue(migration.migrate())

        // token 迁移到新存储
        val info = service.getAuthInfo()
        assertEquals("legacy-at", info.accessToken)
        assertEquals("legacy-rt", info.refreshToken)
        assertEquals("tester", info.username)
        assertTrue(info.isLoggedIn)

        // 旧明文文件已删除（access_token 不再存在）
        val after = context.getSharedPreferences(BaiduAuthService.LEGACY_PLAIN_PREF_NAME, Context.MODE_PRIVATE)
        assertFalse(after.contains("access_token"))

        // 二次迁移幂等：不再发生迁移
        assertFalse(migration.migrate())
    }
}
