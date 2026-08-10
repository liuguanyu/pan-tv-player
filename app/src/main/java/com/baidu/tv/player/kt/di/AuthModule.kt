package com.baidu.tv.player.kt.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 认证 Hilt Module：提供 [EncryptedSharedPreferences]（AES-256 GCM）存储 token。
 *
 * BaiduAuthService / AuthMigration 通过构造函数注入此 SharedPreferences。
 * Keystore 初始化失败时降级为明文 SharedPreferences + 日志告警（防御性降级，见 design.md 风险表）。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthPrefs

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    @AuthPrefs
    fun provideAuthEncryptedSharedPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.e("AuthModule", "EncryptedSharedPreferences 初始化失败，降级明文存储", e)
        context.getSharedPreferences(PLAIN_PREF_FALLBACK, Context.MODE_PRIVATE)
    }

    private const val PREF_NAME = "baidu_auth"
    private const val PLAIN_PREF_FALLBACK = "baidu_auth_plain_fallback"
}
