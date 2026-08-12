package com.baidu.tv.player.kt.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基于 Media3 ExoPlayer 的背景音频播放器生产实现（Phase 5）。
 *
 * 约束：
 * - HTTP: `User-Agent: pan.baidu.com`，`setAllowCrossProtocolRedirects(true)`。
 * - 不设置固定连接/读取超时，不落地缓存。
 * - `repeatMode = REPEAT_MODE_ONE` 实现无限循环。
 * - `volume = 1f`。
 * - 相同 URL [play] 不重新 prepare。
 * - [release] 幂等。
 * - [AudioAttributes] 配置为 `USAGE_MEDIA + AUDIO_CONTENT_TYPE_MUSIC` 并接管音频焦点。
 */
@Singleton
class Media3BackgroundAudioPlayer @Inject constructor(
    @ApplicationContext context: Context,
) : BackgroundAudioPlayer {

    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        volume = 1f
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
    }

    private var currentUrl: String? = null
    private var released = false

    override fun play(url: String, headers: Map<String, String>) {
        if (released) return
        if (currentUrl != url) {
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent("pan.baidu.com")
                .setAllowCrossProtocolRedirects(true)
                .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
            player.setMediaSource(
                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(url)),
            )
            currentUrl = url
            player.prepare()
        }
        player.play()
    }

    override fun pause() {
        if (released) return
        player.pause()
    }

    override fun stop() {
        if (released) return
        player.stop()
        player.clearMediaItems()
        currentUrl = null
    }

    override fun release() {
        if (released) return
        released = true
        player.release()
    }
}
