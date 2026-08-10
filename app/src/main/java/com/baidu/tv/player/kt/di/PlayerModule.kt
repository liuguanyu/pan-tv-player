package com.baidu.tv.player.kt.di

import com.baidu.tv.player.kt.player.HardwareDecoderChecker
import com.baidu.tv.player.kt.player.HybridVideoPlayerEngine
import com.baidu.tv.player.kt.player.Media3VideoPlayerEngine
import com.baidu.tv.player.kt.player.MediaCodecHardwareDecoderChecker
import com.baidu.tv.player.kt.player.MediaMetadataRetrieverCodecInspector
import com.baidu.tv.player.kt.player.PlaybackCapability
import com.baidu.tv.player.kt.player.VideoCodecInspector
import com.baidu.tv.player.kt.player.VideoPlayerEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 播放器相关依赖绑定（对应 Phase 5）。
 *
 * - [VideoPlayerEngine] → [HybridVideoPlayerEngine]（Media3 硬解 + LibVLC/FFmpeg 软解兜底）
 * - [VideoCodecInspector] → [MediaMetadataRetrieverCodecInspector]
 * - [HardwareDecoderChecker] → [MediaCodecHardwareDecoderChecker]
 * - [PlaybackCapability] 由 provide 组装（依赖 HardwareDecoderChecker）
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindVideoPlayerEngine(
        impl: HybridVideoPlayerEngine,
    ): VideoPlayerEngine

    @Binds
    @Singleton
    abstract fun bindVideoCodecInspector(
        impl: MediaMetadataRetrieverCodecInspector,
    ): VideoCodecInspector

    @Binds
    @Singleton
    abstract fun bindHardwareDecoderChecker(
        impl: MediaCodecHardwareDecoderChecker,
    ): HardwareDecoderChecker

    companion object {
        @Provides
        @Singleton
        fun providePlaybackCapability(
            checker: HardwareDecoderChecker,
        ): PlaybackCapability = PlaybackCapability(checker)
    }
}
