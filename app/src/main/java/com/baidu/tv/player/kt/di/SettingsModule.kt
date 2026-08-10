package com.baidu.tv.player.kt.di

import android.content.Context
import android.content.SharedPreferences
import com.baidu.tv.player.kt.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * 设置模块：提供非认证的应用设置 [SharedPreferences]（对应 tasks.md 7.3）。
 *
 * - 使用普通 [SharedPreferences]（非加密）：这些是播放/图片/地点显示等非敏感偏好；
 * - PREF_NAME 与 Java 版 [SettingsRepository.PREF_NAME] 一致，兼容旧数据；
 * - 通过 [Named] 限定符与 [AuthModule] 的 [AuthPrefs] EncryptedSharedPreferences 区分；
 * - [SettingsRepository] 为构造注入（无需在此显式 @Provides）。
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    @Named(SettingsRepository.PREFS_QUALIFIER)
    fun provideSettingsSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences =
        context.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE)
}
