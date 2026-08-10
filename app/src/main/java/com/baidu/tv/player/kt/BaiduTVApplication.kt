package com.baidu.tv.player.kt

import android.app.Application
import com.baidu.tv.player.kt.auth.AuthMigration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 百度网盘 TV 播放器 - Kotlin 版 Application
 *
 * @HiltAndroidApp 触发 Hilt 的代码生成，创建依赖注入容器。
 * 启动时在后台协程执行一次性明文 token → EncryptedSharedPreferences 迁移。
 */
@HiltAndroidApp
class BaiduTVApplication : Application() {

    @Inject lateinit var authMigration: AuthMigration

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { authMigration.migrate() }
    }
}

