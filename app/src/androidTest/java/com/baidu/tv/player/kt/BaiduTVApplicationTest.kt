package com.baidu.tv.player.kt

import androidx.test.core.app.ApplicationProvider
import com.baidu.tv.player.kt.database.AppDatabase
import com.baidu.tv.player.kt.network.BaiduPanService
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * 验证 Hilt DI 图完整注入，无 missing binding。
 * 若任何 @Provides 缺失或绑定不完整，此测试在组件图生成期即编译失败。
 */
@HiltAndroidTest
class BaiduTVApplicationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var appDatabase: AppDatabase
    @Inject lateinit var baiduPanService: BaiduPanService

    @Test
    fun hiltGraphInjectsWithoutMissingBindings() {
        hiltRule.inject()
        // 触发实际创建以确认绑定可实例化
        check(appDatabase.openHelper.databaseName == AppDatabase.DATABASE_NAME)
        // baiduPanService 由 Hilt 注入，非空即表示绑定成功
        check(this.baiduPanService.javaClass.interfaces.isNotEmpty())
    }

    @Test
    fun applicationContextIsBaiduTvApplication() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        check(app is BaiduTVApplication)
    }
}
