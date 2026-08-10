package com.baidu.tv.player.kt.di

import com.baidu.tv.player.kt.location.DefaultVideoMetadataReader
import com.baidu.tv.player.kt.location.VideoMetadataReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 地点识别相关 Hilt 绑定（对应 tasks.md 7.4/7.6）。
 *
 * 将 [VideoMetadataReader] 绑定到默认的 MediaMetadataRetriever 实现，
 * 便于单元测试替换为桩实现验证视频超时/异常路径。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun bindVideoMetadataReader(
        impl: DefaultVideoMetadataReader,
    ): VideoMetadataReader
}
