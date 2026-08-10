package com.baidu.tv.player.kt

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * 自定义 AndroidJUnitRunner：为 Hilt 集成测试使用 [HiltTestApplication]，
 * 使 @HiltAndroidTest 能加载测试组件图（含 [com.baidu.tv.player.kt.di.TestDatabaseModule]）。
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
