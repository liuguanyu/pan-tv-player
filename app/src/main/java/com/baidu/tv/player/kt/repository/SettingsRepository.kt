package com.baidu.tv.player.kt.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * 应用设置的持久化封装（对应 tasks.md 7.2 / 7.3）。
 *
 * 替代 Java 版散落各处的 [com.baidu.tv.player.utils.PreferenceUtils] 静态调用：
 * - 统一通过构造注入的 [SharedPreferences]（由 [com.baidu.tv.player.kt.di.SettingsModule] 提供）读写；
 * - 键名/默认值与 Java 版保持一致，保证旧数据可无缝读取；
 * - 每个设置项暴露一个 [StateFlow]，供 ViewModel 组合成 UI 状态；
 * - 认证 token 仍走 [com.baidu.tv.player.kt.auth.BaiduAuthService] 的 EncryptedSharedPreferences，
 *   此处只承载"非认证"的播放/图片/地点设置，符合 design.md Decision 6 的边界。
 *
 * 注意：这些为非敏感的偏好设置（播放模式、特效、时长、是否显示地点），
 * 不涉及隐私数据，使用普通 SharedPreferences 即可，且便于纯 JVM 单测（Robolectric）。
 */
@Singleton
class SettingsRepository @Inject constructor(
    @Named(PREFS_QUALIFIER) private val prefs: SharedPreferences,
) {

    private val _playMode = MutableStateFlow(readPlayMode())
    val playMode: StateFlow<PlayMode> = _playMode.asStateFlow()

    private val _imageEffect = MutableStateFlow(readImageEffect())
    val imageEffect: StateFlow<ImageEffect> = _imageEffect.asStateFlow()

    private val _backgroundMode = MutableStateFlow(readBackgroundMode())
    val backgroundMode: StateFlow<ImageBackgroundMode> = _backgroundMode.asStateFlow()

    private val _imageDisplayDurationMs = MutableStateFlow(readImageDisplayDuration())
    val imageDisplayDurationMs: StateFlow<Int> = _imageDisplayDurationMs.asStateFlow()

    private val _imageTransitionDurationMs = MutableStateFlow(readImageTransitionDuration())
    val imageTransitionDurationMs: StateFlow<Int> = _imageTransitionDurationMs.asStateFlow()

    private val _showLocation = MutableStateFlow(readShowLocation())
    val showLocation: StateFlow<Boolean> = _showLocation.asStateFlow()

    private val _showCounter = MutableStateFlow(readShowCounter())
    val showCounter: StateFlow<Boolean> = _showCounter.asStateFlow()

    private val _showCaptureTime = MutableStateFlow(readShowCaptureTime())
    val showCaptureTime: StateFlow<Boolean> = _showCaptureTime.asStateFlow()

    private val _bgm = MutableStateFlow(readBgm())
    val bgm: StateFlow<BgmSelection?> = _bgm.asStateFlow()

    // ========== 播放模式 ==========

    fun setPlayMode(mode: PlayMode) {
        prefs.edit { putInt(KEY_PLAY_MODE, mode.value) }
        _playMode.value = mode
    }

    private fun readPlayMode(): PlayMode =
        PlayMode.fromValue(prefs.getInt(KEY_PLAY_MODE, DEFAULT_PLAY_MODE))

    // ========== 图片特效 ==========

    fun setImageEffect(effect: ImageEffect) {
        prefs.edit { putInt(KEY_IMAGE_EFFECT, effect.value) }
        _imageEffect.value = effect
    }

    private fun readImageEffect(): ImageEffect =
        ImageEffect.fromValue(prefs.getInt(KEY_IMAGE_EFFECT, DEFAULT_IMAGE_EFFECT))

    // ========== 图片背景 ==========

    fun setBackgroundMode(mode: ImageBackgroundMode) {
        prefs.edit { putInt(KEY_BACKGROUND_MODE, mode.value) }
        _backgroundMode.value = mode
    }

    private fun readBackgroundMode(): ImageBackgroundMode =
        ImageBackgroundMode.fromValue(prefs.getInt(KEY_BACKGROUND_MODE, DEFAULT_BACKGROUND_MODE))

    // ========== 图片展示时长（毫秒） ==========

    fun setImageDisplayDurationMs(durationMs: Int) {
        val safe = durationMs.coerceAtLeast(MIN_DISPLAY_DURATION_MS)
        prefs.edit { putInt(KEY_IMAGE_DISPLAY_DURATION, safe) }
        _imageDisplayDurationMs.value = safe
    }

    private fun readImageDisplayDuration(): Int =
        prefs.getInt(KEY_IMAGE_DISPLAY_DURATION, DEFAULT_IMAGE_DISPLAY_DURATION)

    // ========== 图片过渡时长（毫秒） ==========

    fun setImageTransitionDurationMs(durationMs: Int) {
        val safe = durationMs.coerceAtLeast(0)
        prefs.edit { putInt(KEY_IMAGE_TRANSITION_DURATION, safe) }
        _imageTransitionDurationMs.value = safe
    }

    private fun readImageTransitionDuration(): Int =
        prefs.getInt(KEY_IMAGE_TRANSITION_DURATION, DEFAULT_IMAGE_TRANSITION_DURATION)

    // ========== 是否启用地点识别显示 ==========

    fun setShowLocation(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_LOCATION, show) }
        _showLocation.value = show
    }

    private fun readShowLocation(): Boolean =
        prefs.getBoolean(KEY_SHOW_LOCATION, DEFAULT_SHOW_LOCATION)

    // ========== 是否显示播放计数器（n/total） ==========

    fun setShowCounter(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_COUNTER, show) }
        _showCounter.value = show
    }

    private fun readShowCounter(): Boolean =
        prefs.getBoolean(KEY_SHOW_COUNTER, DEFAULT_SHOW_COUNTER)

    // ========== 是否显示拍摄时间 ==========

    fun setShowCaptureTime(show: Boolean) {
        prefs.edit { putBoolean(KEY_SHOW_CAPTURE_TIME, show) }
        _showCaptureTime.value = show
    }

    private fun readShowCaptureTime(): Boolean =
        prefs.getBoolean(KEY_SHOW_CAPTURE_TIME, DEFAULT_SHOW_CAPTURE_TIME)

    fun setBgm(selection: BgmSelection?) {
        prefs.edit {
            if (selection == null) {
                remove(KEY_BGM_FS_ID).remove(KEY_BGM_PATH).remove(KEY_BGM_NAME)
            } else {
                putLong(KEY_BGM_FS_ID, selection.fsId)
                    .putString(KEY_BGM_PATH, selection.path)
                    .putString(KEY_BGM_NAME, selection.name)
            }
        }
        _bgm.value = selection
    }

    private fun readBgm(): BgmSelection? {
        val fsId = prefs.getLong(KEY_BGM_FS_ID, 0L)
        return if (fsId > 0L) BgmSelection(fsId, prefs.getString(KEY_BGM_PATH, null), prefs.getString(KEY_BGM_NAME, null)) else null
    }

    companion object {
        /** Hilt Named 限定符：设置专用 SharedPreferences。 */
        const val PREFS_QUALIFIER = "settings_prefs"

        /** 与 Java 版 PreferenceUtils.PREF_NAME 保持一致，兼容旧数据。 */
        const val PREF_NAME = "baidu_tv_player"

        const val KEY_IMAGE_EFFECT = "image_effect"
        const val KEY_IMAGE_DISPLAY_DURATION = "image_display_duration"
        const val KEY_IMAGE_TRANSITION_DURATION = "image_transition_duration"
        const val KEY_SHOW_LOCATION = "show_location"
        const val KEY_SHOW_COUNTER = "show_counter"
        const val KEY_SHOW_CAPTURE_TIME = "show_capture_time"
        const val KEY_PLAY_MODE = "play_mode"
        const val KEY_BACKGROUND_MODE = "background_mode"
        const val KEY_BGM_FS_ID = "bgm_fs_id"
        const val KEY_BGM_PATH = "bgm_path"
        const val KEY_BGM_NAME = "bgm_name"

        const val DEFAULT_IMAGE_EFFECT = 0 // 淡入淡出
        const val DEFAULT_IMAGE_DISPLAY_DURATION = 10_000 // 10 秒
        const val DEFAULT_IMAGE_TRANSITION_DURATION = 1_000 // 1 秒
        const val DEFAULT_SHOW_LOCATION = true
        const val DEFAULT_SHOW_COUNTER = true
        const val DEFAULT_SHOW_CAPTURE_TIME = true
        const val DEFAULT_PLAY_MODE = 0 // 顺序播放
        const val DEFAULT_BACKGROUND_MODE = 1 // 主色调背景

        const val MIN_DISPLAY_DURATION_MS = 1_000
    }
}
