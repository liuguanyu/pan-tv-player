package com.baidu.tv.player.kt.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Media3BackgroundAudioPlayer 行为测试（Tasks 5.3–5.4）。
 *
 * 使用 Robolectric 提供ApplicationContext，验证：
 * - 相同 URL 恢复不重复 prepare。
 * - URL 变化替换 source。
 * - 幂等 release。
 * - AudioAttributes 配置正确。
 */
@RunWith(RobolectricTestRunner::class)
class Media3BackgroundAudioPlayerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun sameUrl_play_doesNotReprepare() {
        val player = Media3BackgroundAudioPlayer(context)
        val headers = mapOf("Authorization" to "Bearer token")

        // 第一次 play 应 prepare 并开始播放
        player.play("http://example.com/bgm.mp3", headers)
        val stateAfterFirst = player.testPlayerState()

        player.pause()
        // 第二次 play 相同 URL 应仅恢复播放，不重新 prepare
        player.play("http://example.com/bgm.mp3", headers)
        val stateAfterSecond = player.testPlayerState()

        // playbackState 在 prepare 时会回到 STATE_BUFFERING；
        // 不重新 prepare 时应保持当前状态不变
        assertEquals(stateAfterFirst, stateAfterSecond)

        player.release()
    }

    @Test
    fun differentUrl_play_replacesSource() {
        val player = Media3BackgroundAudioPlayer(context)

        player.play("http://example.com/a.mp3", emptyMap())
        val firstMediaItemCount = player.testPlayerMediaItemCount()

        player.play("http://example.com/b.mp3", emptyMap())
        val secondMediaItemCount = player.testPlayerMediaItemCount()

        // 替换 source 后仍应只有 1 个 media item
        assertEquals(1, firstMediaItemCount)
        assertEquals(1, secondMediaItemCount)

        player.release()
    }

    @Test
    fun release_calledTwice_doesNotCrash() {
        val player = Media3BackgroundAudioPlayer(context)

        player.play("http://example.com/bgm.mp3", emptyMap())
        player.release()

        // 第二次 release 不应抛异常
        player.release()
    }

    @Test
    fun release_then_play_isNoOp() {
        val player = Media3BackgroundAudioPlayer(context)

        player.release()
        // release 后调用 play 不应崩溃
        player.play("http://example.com/bgm.mp3", emptyMap())
        player.pause()
        player.stop()
    }

    @Test
    fun player_configuredWithCorrectAudioAttributes() {
        val player = Media3BackgroundAudioPlayer(context)
        val attrs = player.testAudioAttributes()

        assertEquals(C.USAGE_MEDIA, attrs.usage)
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, attrs.contentType)

        player.release()
    }

    @Test
    fun player_configuredWithRepeatModeOne() {
        val player = Media3BackgroundAudioPlayer(context)

        assertEquals(Player.REPEAT_MODE_ONE, player.testRepeatMode())

        player.release()
    }

    @Test
    fun player_configuredWithVolumeOne() {
        val player = Media3BackgroundAudioPlayer(context)

        assertEquals(1f, player.testVolume(), 0.001f)

        player.release()
    }

    @Test
    fun stop_clearsMediaItems() {
        val player = Media3BackgroundAudioPlayer(context)

        player.play("http://example.com/bgm.mp3", emptyMap())
        assertEquals(1, player.testPlayerMediaItemCount())

        player.stop()
        assertEquals(0, player.testPlayerMediaItemCount())

        player.release()
    }

    // ---- 辅助：通过反射访问内部 ExoPlayer 状态用于测试断言 ----

    private fun Media3BackgroundAudioPlayer.testPlayerState(): Int {
        val playerField = Media3BackgroundAudioPlayer::class.java
            .getDeclaredField("player")
        playerField.isAccessible = true
        val exoPlayer = playerField.get(this) as Player
        return exoPlayer.playbackState
    }

    private fun Media3BackgroundAudioPlayer.testPlayerMediaItemCount(): Int {
        val playerField = Media3BackgroundAudioPlayer::class.java
            .getDeclaredField("player")
        playerField.isAccessible = true
        val exoPlayer = playerField.get(this) as Player
        return exoPlayer.mediaItemCount
    }

    private fun Media3BackgroundAudioPlayer.testAudioAttributes(): AudioAttributes {
        val playerField = Media3BackgroundAudioPlayer::class.java
            .getDeclaredField("player")
        playerField.isAccessible = true
        val exoPlayer = playerField.get(this) as Player
        return exoPlayer.audioAttributes
    }

    private fun Media3BackgroundAudioPlayer.testRepeatMode(): Int {
        val playerField = Media3BackgroundAudioPlayer::class.java
            .getDeclaredField("player")
        playerField.isAccessible = true
        val exoPlayer = playerField.get(this) as Player
        return exoPlayer.repeatMode
    }

    private fun Media3BackgroundAudioPlayer.testVolume(): Float {
        val playerField = Media3BackgroundAudioPlayer::class.java
            .getDeclaredField("player")
        playerField.isAccessible = true
        val exoPlayer = playerField.get(this) as Player
        return exoPlayer.volume
    }
}
