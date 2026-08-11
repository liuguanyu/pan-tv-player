package com.baidu.tv.player.kt.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer

/** 独立于视频引擎的网盘背景音乐播放器，仅流式播放，不落地缓存完整音频。 */
class BackgroundMusicPlayer(context: Context) {
    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        repeatMode = Player.REPEAT_MODE_ONE
        volume = 1f
    }

    private var currentUrl: String? = null

    fun play(url: String, headers: Map<String, String>) {
        if (currentUrl != url) {
            val factory = DefaultHttpDataSource.Factory()
                .setUserAgent("pan.baidu.com")
                .setAllowCrossProtocolRedirects(true)
                .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
            player.setMediaSource(
                androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(MediaItem.fromUri(url)),
            )
            currentUrl = url
            player.prepare()
        }
        player.play()
    }

    fun pause() { player.pause() }
    fun resume() { player.play() }
    fun stop() {
        player.stop()
        player.clearMediaItems()
        currentUrl = null
    }
    fun release() { player.release() }
}
