package com.baidu.tv.player.kt.di

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import com.baidu.tv.player.kt.database.AppDatabase
import com.baidu.tv.player.kt.database.PlaybackHistoryDao
import com.baidu.tv.player.kt.database.PlaylistDao
import com.baidu.tv.player.kt.database.PlaylistItemDao
import com.baidu.tv.player.kt.repository.TransactionRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据库 Hilt Module：提供 AppDatabase 单例 + 各 DAO。
 * 替代 Java 版 AppDatabase 的手动双检锁单例。
 * 不使用 fallbackToDestructiveMigration。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .build()

    @Provides
    fun providePlaylistDao(db: AppDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun providePlaylistItemDao(db: AppDatabase): PlaylistItemDao = db.playlistItemDao()

    @Provides
    fun providePlaybackHistoryDao(db: AppDatabase): PlaybackHistoryDao = db.playbackHistoryDao()

    @Provides
    @Singleton
    fun provideTransactionRunner(db: AppDatabase): TransactionRunner =
        TransactionRunner { block -> db.withTransaction { block() } }
}
