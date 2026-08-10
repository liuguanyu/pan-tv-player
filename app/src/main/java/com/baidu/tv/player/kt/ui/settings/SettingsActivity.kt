package com.baidu.tv.player.kt.ui.settings

import android.app.AlertDialog
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
 * TV 友好：每个设置项为可聚焦行，D-pad 上下导航，OK/中键打开带 RadioButton 的单选菜单。
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
        binding.rowPlayMode.setOnClickListener { showPlayModeMenu() }
        binding.rowImageEffect.setOnClickListener { showImageEffectMenu() }
        binding.rowBackground.setOnClickListener { showBackgroundModeMenu() }
        binding.rowDisplayDuration.setOnClickListener { showDisplayDurationMenu() }
        binding.rowTransitionDuration.setOnClickListener { showTransitionDurationMenu() }
        binding.rowShowLocation.setOnClickListener {
            showBooleanMenu(getString(R.string.settings_show_location), viewModel.uiState.value.showLocation) {
                viewModel.setShowLocation(it)
            }
        }
        binding.rowShowCounter.setOnClickListener {
            showBooleanMenu(getString(R.string.settings_show_counter), viewModel.uiState.value.showCounter) {
                viewModel.setShowCounter(it)
            }
        }
        binding.rowShowCaptureTime.setOnClickListener {
            showBooleanMenu(getString(R.string.settings_show_capture_time), viewModel.uiState.value.showCaptureTime) {
                viewModel.setShowCaptureTime(it)
            }
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

    private fun showPlayModeMenu() {
        val options = PlayMode.entries
        showSingleChoiceMenu(
            title = getString(R.string.settings_play_mode),
            labels = options.map { it.displayName },
            checkedIndex = options.indexOf(viewModel.uiState.value.playMode),
        ) { viewModel.setPlayMode(options[it]) }
    }

    private fun showImageEffectMenu() {
        // RANDOM 的持久化 value 保持不变，只调整菜单顺序，让「随机」位于全部具体动效之后。
        val options = ImageEffect.entries.filterNot { it == ImageEffect.RANDOM } + ImageEffect.RANDOM
        showSingleChoiceMenu(
            title = getString(R.string.settings_image_effect),
            labels = options.map { it.displayName },
            checkedIndex = options.indexOf(viewModel.uiState.value.imageEffect),
        ) { viewModel.setImageEffect(options[it]) }
    }

    private fun showBackgroundModeMenu() {
        val options = ImageBackgroundMode.entries
        showSingleChoiceMenu(
            title = getString(R.string.settings_background_mode),
            labels = options.map { it.displayName },
            checkedIndex = options.indexOf(viewModel.uiState.value.backgroundMode),
        ) { viewModel.setBackgroundMode(options[it]) }
    }

    private fun showDisplayDurationMenu() {
        val options = (MIN_DISPLAY_MS..MAX_DISPLAY_MS step 1_000).toList()
        showSingleChoiceMenu(
            title = getString(R.string.settings_display_duration),
            labels = options.map { getString(R.string.settings_seconds_format, formatSeconds(it)) },
            checkedIndex = options.indexOf(viewModel.uiState.value.imageDisplayDurationMs),
        ) { viewModel.setImageDisplayDurationMs(options[it]) }
    }

    private fun showTransitionDurationMenu() {
        val options = (0..MAX_TRANSITION_MS step 500).toList()
        showSingleChoiceMenu(
            title = getString(R.string.settings_transition_duration),
            labels = options.map { getString(R.string.settings_seconds_format, formatSeconds(it)) },
            checkedIndex = options.indexOf(viewModel.uiState.value.imageTransitionDurationMs),
        ) { viewModel.setImageTransitionDurationMs(options[it]) }
    }

    private fun showBooleanMenu(title: String, current: Boolean, onSelected: (Boolean) -> Unit) {
        val options = listOf(true, false)
        showSingleChoiceMenu(
            title = title,
            labels = listOf(getString(R.string.settings_on), getString(R.string.settings_off)),
            checkedIndex = options.indexOf(current),
        ) { onSelected(options[it]) }
    }

    /** AlertDialog 的 single-choice 列表在每一项前显示 RadioButton，适合遥控器一次查看并选择。 */
    private fun showSingleChoiceMenu(
        title: String,
        labels: List<String>,
        checkedIndex: Int,
        onSelected: (Int) -> Unit,
    ) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels.toTypedArray(), checkedIndex) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.listView.requestFocus()
            dialog.listView.setSelection(checkedIndex.coerceAtLeast(0))
        }
        dialog.show()
    }

    companion object {
        private const val MIN_DISPLAY_MS = 3_000
        private const val MAX_DISPLAY_MS = 30_000
        private const val MAX_TRANSITION_MS = 3_000
    }
}
