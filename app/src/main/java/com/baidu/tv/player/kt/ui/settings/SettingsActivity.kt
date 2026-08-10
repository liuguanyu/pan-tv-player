package com.baidu.tv.player.kt.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ActivitySettingsBinding
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * 设置页（对应 tasks.md 7.1）。
 *
 * TV 友好：每个设置项为可聚焦行，D-pad 上下导航、OK/中键点击循环切换值。
 * 所有状态来自 [SettingsViewModel.uiState]，通过 repeatOnLifecycle(STARTED) 收集；
 * 一次性提示 / 地点识别测试结果通过 [SettingsViewModel.events] 收集。
 */
@AndroidEntryPoint
class SettingsActivity : FragmentActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupRows()
        collectState()
        binding.rowPlayMode.requestFocus()
    }

    private fun setupRows() {
        binding.rowPlayMode.setOnClickListener {
            viewModel.setPlayMode(nextPlayMode(viewModel.uiState.value.playMode))
        }
        binding.rowImageEffect.setOnClickListener {
            viewModel.setImageEffect(nextImageEffect(viewModel.uiState.value.imageEffect))
        }
        binding.rowBackground.setOnClickListener {
            viewModel.setBackgroundMode(nextBackgroundMode(viewModel.uiState.value.backgroundMode))
        }
        binding.rowDisplayDuration.setOnClickListener {
            viewModel.setImageDisplayDurationMs(
                nextDisplayDuration(viewModel.uiState.value.imageDisplayDurationMs),
            )
        }
        binding.rowTransitionDuration.setOnClickListener {
            viewModel.setImageTransitionDurationMs(
                nextTransitionDuration(viewModel.uiState.value.imageTransitionDurationMs),
            )
        }
        binding.rowShowLocation.setOnClickListener {
            viewModel.setShowLocation(!viewModel.uiState.value.showLocation)
        }
        binding.rowShowCounter.setOnClickListener {
            viewModel.setShowCounter(!viewModel.uiState.value.showCounter)
        }
        binding.rowShowCaptureTime.setOnClickListener {
            viewModel.setShowCaptureTime(!viewModel.uiState.value.showCaptureTime)
        }
    }

    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { render(it) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is SettingsUiEvent.ShowToast ->
                                Toast.makeText(this@SettingsActivity, event.message, Toast.LENGTH_SHORT).show()
                            is SettingsUiEvent.LocationTestResult -> {
                                val msg = event.address ?: getString(R.string.settings_off)
                                Toast.makeText(this@SettingsActivity, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun render(state: SettingsUiState) {
        binding.valuePlayMode.text = state.playMode.displayName
        binding.valueImageEffect.text = state.imageEffect.displayName
        binding.valueBackground.text = state.backgroundMode.displayName
        binding.valueDisplayDuration.text =
            getString(R.string.settings_seconds_format, formatSeconds(state.imageDisplayDurationMs))
        binding.valueTransitionDuration.text =
            getString(R.string.settings_seconds_format, formatSeconds(state.imageTransitionDurationMs))
        binding.valueShowLocation.text =
            getString(if (state.showLocation) R.string.settings_on else R.string.settings_off)
        binding.valueShowCounter.text =
            getString(if (state.showCounter) R.string.settings_on else R.string.settings_off)
        binding.valueShowCaptureTime.text =
            getString(if (state.showCaptureTime) R.string.settings_on else R.string.settings_off)
    }

    private fun formatSeconds(ms: Int): String {
        val seconds = ms / 1000.0
        return if (seconds == seconds.toLong().toDouble()) {
            seconds.toLong().toString()
        } else {
            String.format("%.1f", seconds)
        }
    }

    private fun nextPlayMode(current: PlayMode): PlayMode {
        val values = PlayMode.entries
        return values[(current.ordinal + 1) % values.size]
    }

    private fun nextImageEffect(current: ImageEffect): ImageEffect {
        val values = ImageEffect.entries
        return values[(current.ordinal + 1) % values.size]
    }

    private fun nextBackgroundMode(current: ImageBackgroundMode): ImageBackgroundMode {
        val values = ImageBackgroundMode.entries
        return values[(current.ordinal + 1) % values.size]
    }

    /** 幻灯片间隔在 [3s, 30s] 之间以 1s 步进循环。 */
    private fun nextDisplayDuration(currentMs: Int): Int {
        val next = currentMs + 1_000
        return if (next > MAX_DISPLAY_MS) MIN_DISPLAY_MS else next
    }

    /** 过渡时长在 [0s, 3s] 之间以 0.5s 步进循环。 */
    private fun nextTransitionDuration(currentMs: Int): Int {
        val next = currentMs + 500
        return if (next > MAX_TRANSITION_MS) 0 else next
    }

    companion object {
        private const val MIN_DISPLAY_MS = 3_000
        private const val MAX_DISPLAY_MS = 30_000
        private const val MAX_TRANSITION_MS = 3_000
    }
}
