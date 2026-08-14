package com.baidu.tv.player.kt.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.location.LocationExtractionService
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.repository.BgmSelection
import com.baidu.tv.player.kt.repository.SettingsRepository
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页 UI 状态（对应 tasks.md 7.2）。 */
data class SettingsUiState(
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val imageEffect: ImageEffect = ImageEffect.FADE,
    val backgroundMode: ImageBackgroundMode = ImageBackgroundMode.DOMINANT_COLOR,
    val imageDisplayDurationMs: Int = SettingsRepository.DEFAULT_IMAGE_DISPLAY_DURATION,
    val imageTransitionDurationMs: Int = SettingsRepository.DEFAULT_IMAGE_TRANSITION_DURATION,
    val showLocation: Boolean = true,
    val showCounter: Boolean = true,
    val showCaptureTime: Boolean = true,
    val bgm: BgmSelection? = null,
)

/** 设置页一次性事件。 */
sealed interface SettingsUiEvent {
    data class ShowToast(val message: String) : SettingsUiEvent
    /** 地点识别测试结果（成功地址或失败提示）。 */
    data class LocationTestResult(val address: String?) : SettingsUiEvent
}

/**
 * 设置页 ViewModel（对应 tasks.md 7.2）。
 *
 * - 通过 [SettingsRepository] 读写所有设置项，将各 [StateFlow] 组合为单一 [uiState]；
 * - 一次性提示 / 地点识别测试结果通过 [events] ([SharedFlow]) 发出；
 * - 地点识别测试入口（tasks.md 7.4）调用 [LocationExtractionService]，全程协程，失败静默。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val locationExtractionService: LocationExtractionService,
    private val authService: BaiduAuthService,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.playMode,
        settingsRepository.imageEffect,
        settingsRepository.backgroundMode,
        settingsRepository.imageDisplayDurationMs,
        combine(
            settingsRepository.imageTransitionDurationMs,
            settingsRepository.showLocation,
            settingsRepository.showCounter,
            settingsRepository.showCaptureTime,
            settingsRepository.bgm,
        ) { transition, showLocation, showCounter, showCaptureTime, bgm ->
            SecondarySettings(transition, showLocation, showCounter, showCaptureTime, bgm)
        },
    ) { playMode, effect, background, display, secondary ->
        SettingsUiState(
            playMode = playMode,
            imageEffect = effect,
            backgroundMode = background,
            imageDisplayDurationMs = display,
            imageTransitionDurationMs = secondary.transitionMs,
            showLocation = secondary.showLocation,
            showCounter = secondary.showCounter,
            showCaptureTime = secondary.showCaptureTime,
            bgm = secondary.bgm,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    /** 内层 combine 的中间状态（kotlinx combine 单组最多 5 个流）。 */
    private data class SecondarySettings(
        val transitionMs: Int,
        val showLocation: Boolean,
        val showCounter: Boolean,
        val showCaptureTime: Boolean,
        val bgm: BgmSelection?,
    )

    private val _events = MutableSharedFlow<SettingsUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsUiEvent> = _events.asSharedFlow()

    fun setPlayMode(mode: PlayMode) = settingsRepository.setPlayMode(mode)

    fun setImageEffect(effect: ImageEffect) = settingsRepository.setImageEffect(effect)

    fun setBackgroundMode(mode: ImageBackgroundMode) = settingsRepository.setBackgroundMode(mode)

    fun setImageDisplayDurationMs(durationMs: Int) =
        settingsRepository.setImageDisplayDurationMs(durationMs)

    fun setImageTransitionDurationMs(durationMs: Int) =
        settingsRepository.setImageTransitionDurationMs(durationMs)

    fun setShowLocation(show: Boolean) = settingsRepository.setShowLocation(show)

    fun setShowCounter(show: Boolean) = settingsRepository.setShowCounter(show)

    fun setShowCaptureTime(show: Boolean) = settingsRepository.setShowCaptureTime(show)

    fun setBgm(selection: BgmSelection?) = settingsRepository.setBgm(selection)

    /** 清除本地认证信息；页面负责随后跳转到登录页。 */
    fun logout() = authService.logout()

    /**
     * 地点识别测试入口（tasks.md 7.4）：给定媒体 URL 尝试解析地址并通过事件回传。
     * 失败静默返回 null（不弹错误），仅以事件形式提示。
     */
    fun testLocationExtraction(url: String, isVideo: Boolean) {
        viewModelScope.launch {
            val address = locationExtractionService.extractLocation(url, isVideo)
            _events.emit(SettingsUiEvent.LocationTestResult(address))
        }
    }
}
