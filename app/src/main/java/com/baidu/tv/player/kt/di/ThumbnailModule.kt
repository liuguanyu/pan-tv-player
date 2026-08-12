package com.baidu.tv.player.kt.di

import com.baidu.tv.player.kt.repository.FileRepositoryThumbnailProvider
import com.baidu.tv.player.kt.repository.ThumbnailProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 缩略图数据边界绑定（Phase 10.5）。
 *
 * - [ThumbnailProvider] → [FileRepositoryThumbnailProvider]
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ThumbnailModule {

    @Binds
    @Singleton
    abstract fun bindThumbnailProvider(
        impl: FileRepositoryThumbnailProvider,
    ): ThumbnailProvider
}
