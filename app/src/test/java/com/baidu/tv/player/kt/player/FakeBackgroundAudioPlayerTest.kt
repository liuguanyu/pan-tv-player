package com.baidu.tv.player.kt.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FakeBackgroundAudioPlayer 调用序列测试（Task 5.1）。
 *
 * 验证假身正确记录 play/pause/stop/release 的调用顺序与参数，
 * 以及"相同 URL 不重新 prepare"的语义模拟。
 */
class FakeBackgroundAudioPlayerTest {

    @Test
    fun play_pause_stop_release_recordsInOrder() {
        val fake = FakeBackgroundAudioPlayer()
        val headers = mapOf("Authorization" to "Bearer token")

        fake.play("http://a.com/music.mp3", headers)
        fake.pause()
        fake.stop()
        fake.release()

        assertEquals(5, fake.calls.size)
        assertTrue(fake.calls[0] is FakeBackgroundAudioPlayer.Call.Play)
        assertTrue(fake.calls[1] is FakeBackgroundAudioPlayer.Call.Prepare)
        assertTrue(fake.calls[2] is FakeBackgroundAudioPlayer.Call.Pause)
        assertTrue(fake.calls[3] is FakeBackgroundAudioPlayer.Call.Stop)
        assertTrue(fake.calls[4] is FakeBackgroundAudioPlayer.Call.Release)
    }

    @Test
    fun play_recordsUrlAndHeaders() {
        val fake = FakeBackgroundAudioPlayer()
        val headers = mapOf("Range" to "bytes=0-")

        fake.play("http://b.com/audio.mp3", headers)

        val playCall = fake.calls.filterIsInstance<FakeBackgroundAudioPlayer.Call.Play>().first()
        assertEquals("http://b.com/audio.mp3", playCall.url)
        assertEquals(headers, playCall.headers)
    }

    @Test
    fun sameUrl_play_doesNotPrepareAgain() {
        val fake = FakeBackgroundAudioPlayer()

        fake.play("http://a.com/music.mp3", emptyMap())
        fake.pause()
        fake.play("http://a.com/music.mp3", emptyMap())

        // 第一次 play → Play + Prepare；pause → Pause；第二次 play → Play（无 Prepare）
        assertEquals(4, fake.calls.size)
        assertEquals(1, fake.prepareCount)
    }

    @Test
    fun differentUrl_play_preparesAgain() {
        val fake = FakeBackgroundAudioPlayer()

        fake.play("http://a.com/music.mp3", emptyMap())
        fake.play("http://b.com/music.mp3", emptyMap())

        assertEquals(2, fake.prepareCount)
    }

    @Test
    fun stop_resetsUrl_soNextPlayPrepares() {
        val fake = FakeBackgroundAudioPlayer()

        fake.play("http://a.com/music.mp3", emptyMap())
        fake.stop()
        fake.play("http://a.com/music.mp3", emptyMap())

        assertEquals(2, fake.prepareCount)
    }

    @Test
    fun release_makesSubsequentCallsNoOp() {
        val fake = FakeBackgroundAudioPlayer()

        fake.play("http://a.com/music.mp3", emptyMap())
        fake.release()
        fake.play("http://b.com/music.mp3", emptyMap())
        fake.pause()
        fake.stop()

        val releaseIndex = fake.calls.indexOfFirst { it is FakeBackgroundAudioPlayer.Call.Release }
        // release 之后不应有任何调用
        assertEquals(fake.calls.size - 1, releaseIndex)
    }
}
