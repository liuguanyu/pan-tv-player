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
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * 测试用 DatabaseModule：用内存数据库替代真实数据库，测试间隔离。
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class],
)
object TestDatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
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
