package com.baidu.tv.player.kt.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SettingsRepository] 持久化测试（对应 tasks.md 7.9）。
 *
 * 使用 Robolectric 提供的真实 SharedPreferences，验证：
 * - 默认值与 Java 版一致；
 * - 写入后可从新实例读回（真正落盘）；
 * - StateFlow 同步更新。
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 清空避免测试间污染。
        context.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun newRepository(): SettingsRepository {
        val prefs = context.getSharedPreferences(SettingsRepository.PREF_NAME, Context.MODE_PRIVATE)
        return SettingsRepository(prefs)
    }

    @Test
    fun defaults_matchJavaVersion() {
        val repo = newRepository()
        assertEquals(PlayMode.SEQUENTIAL, repo.playMode.value)
        assertEquals(ImageEffect.FADE, repo.imageEffect.value)
        assertEquals(ImageBackgroundMode.DOMINANT_COLOR, repo.backgroundMode.value)
        assertEquals(10_000, repo.imageDisplayDurationMs.value)
        assertEquals(1_000, repo.imageTransitionDurationMs.value)
        assertTrue(repo.showLocation.value)
    }

    @Test
    fun setPlayMode_persistsAndUpdatesFlow() {
        val repo = newRepository()
        repo.setPlayMode(PlayMode.RANDOM)
        assertEquals(PlayMode.RANDOM, repo.playMode.value)
        // 新实例读回，证明落盘。
        assertEquals(PlayMode.RANDOM, newRepository().playMode.value)
    }

    @Test
    fun setImageEffectAndBackground_persist() {
        val repo = newRepository()
        repo.setImageEffect(ImageEffect.ZOOM)
        repo.setBackgroundMode(ImageBackgroundMode.BLUR)
        val reloaded = newRepository()
        assertEquals(ImageEffect.ZOOM, reloaded.imageEffect.value)
        assertEquals(ImageBackgroundMode.BLUR, reloaded.backgroundMode.value)
    }

    @Test
    fun setDisplayDuration_coercedToMinimum() {
        val repo = newRepository()
        repo.setImageDisplayDurationMs(100) // 低于最小值 1000
        assertEquals(SettingsRepository.MIN_DISPLAY_DURATION_MS, repo.imageDisplayDurationMs.value)
    }

    @Test
    fun setShowLocation_persists() {
        val repo = newRepository()
        repo.setShowLocation(false)
        assertFalse(newRepository().showLocation.value)
    }
}
