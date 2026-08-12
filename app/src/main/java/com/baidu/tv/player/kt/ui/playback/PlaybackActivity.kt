package com.baidu.tv.player.kt.ui.playback

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable

import android.os.Bundle
import android.view.KeyEvent

import android.view.Surface
import android.util.Log

import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ActivityPlaybackBinding
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.player.BackgroundMusicCoordinator
import com.baidu.tv.player.kt.player.HybridVideoPlayerEngine
import com.baidu.tv.player.kt.player.Media3VideoPlayerEngine
import com.baidu.tv.player.kt.player.PlaybackResult
import com.baidu.tv.player.kt.player.UnsupportedReason
import com.baidu.tv.player.kt.player.VideoPlayerEngine
import com.baidu.tv.player.kt.repository.SettingsRepository
import com.baidu.tv.player.kt.repository.ThumbnailProvider
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundFactory
import com.baidu.tv.player.kt.ui.playback.image.ImageEffectFactory
import com.baidu.tv.player.kt.ui.settings.SettingsActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.transition.Transition
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject

private const val CONTROL_AUTO_HIDE_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
private const val PROGRESS_INTERVAL_MS = 1_000L
private const val THUMBNAIL_CAPTURE_RETRIES = 5
private const val THUMBNAIL_CAPTURE_DELAY_MS = 800L
private const val FIRST_PLAY_RETRY_DELAY_MS = 600L
private const val TAG = "PlaybackActivity"
private const val VIDEO_BACKGROUND_MAX_EDGE = 640

/**
 * 播放页（Phase 13）：仅负责 Android 生命周期、View/Surface 渲染、遥控器按键转发和协调器委托。
 *
 * 重逻辑全部委托：
 * - 播放状态与用户意图 → [PlaybackViewModel]
 * - BGM 生命周期 → [BackgroundMusicCoordinator]
 * - 快速选播 → [QuickSelectorController]
 * - 视频播放 → [VideoPlayerEngine]
 * - 缩略图 → [ThumbnailProvider]
 * - 布局计算 → [VideoLayoutCalculator]
 * - Surface 可见性状态 → [VideoSurfaceState]
 */
@AndroidEntryPoint
class PlaybackActivity : FragmentActivity(), Media3VideoPlayerEngine.Listener {

    private lateinit var binding: ActivityPlaybackBinding
    private val viewModel: PlaybackViewModel by viewModels()

    @Inject lateinit var videoPlayerEngine: VideoPlayerEngine
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var thumbnailProvider: ThumbnailProvider

    private var controlsVisible = false
    private val selectAdapter = PlaylistQuickSelectorAdapter()
    private lateinit var quickSelector: QuickSelectorController
    private var autoHideJob: Job? = null
    private var progressJob: Job? = null
    private var pendingVideo: Pair<String, FileInfo>? = null
    /** 首次启动可能遇到 Surface/解码器尚未稳定，只对同一文件自动重试一次。 */
    private var retriedFilePath: String? = null
    private var fatalErrorShown = false
    private var resumePlaybackOnReturn = false
    private var videoOutputSurface: Surface? = null
    private var currentImageBitmap: Bitmap? = null
    private var lastPresentationState: Pair<Int, String>? = null
    @Inject lateinit var bgmCoordinator: BackgroundMusicCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // TextureView 与普通 View 在同一合成层，可在 Sony Android 9 上可靠应用旋转，
        // 同时不会像 SurfaceView 那样挖穿背景或在旋转后只剩声音。
        hideSystemBars()
        setupButtons()
        setupQuickSelector()
        setupSeekBar()
        bindEngineListener()
        collectStateAndEvents()
        initializeFromIntent()
    }

    private fun initializeFromIntent() {
        val playlistDatabaseId = intent.getLongExtra("playlistDatabaseId", -1L)
        val historyId = intent.getLongExtra("historyId", -1L)
        when {
            playlistDatabaseId > 0L -> viewModel.initializeFromDatabasePlaylist(playlistDatabaseId)
            historyId > 0L -> viewModel.initializeFromHistory(historyId)
            else -> viewModel.initialize(
                playlistId = intent.getStringExtra("playlistId").orEmpty(),
                mediaType = intent.getIntExtra("mediaType", MediaType.ALL.code),
                folderPath = intent.getStringExtra("folderPath").orEmpty(),
                startIndex = intent.getIntExtra("startIndex", 0),
                selectedPath = intent.getStringExtra("selectedPath"),
            )
        }
    }

    private fun bindEngineListener() {
        when (val engine = videoPlayerEngine) {
            is HybridVideoPlayerEngine -> engine.listener = this
            is Media3VideoPlayerEngine -> engine.listener = this
        }
    }

    private fun setupButtons() = with(binding) {
        previousButton.setOnClickListener { viewModel.playPrevious() }
        nextButton.setOnClickListener { viewModel.playNext() }
        playPauseButton.setOnClickListener { togglePlayback() }
        playModeButton.setOnClickListener { viewModel.cyclePlayMode() }
    }

    private fun setupSeekBar() {
        binding.progressSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = viewModel.uiState.value.durationMs
                        val target = (duration * progress / 1000f).toLong()
                        videoPlayerEngine.seekTo(target)
                        viewModel.updateProgress(target, duration)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = showControls()
                override fun onStopTrackingTouch(seekBar: SeekBar?) = scheduleAutoHide()
            },
        )
    }

    private fun collectStateAndEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { renderState(it) }
                }
                launch {
                    viewModel.events.collect { event -> handleEvent(event) }
                }
                launch {
                    settingsRepository.bgm.collect { selection ->
                        bgmCoordinator.setSelection(selection, buildRequestHeaders())
                    }
                }
            }
        }
    }

    private fun renderState(state: PlaybackUiState) = with(binding) {
        loadingProgress.visibility = if (state.isLoading) View.VISIBLE else View.GONE
        fileNameText.text = state.currentFile?.serverFilename.orEmpty()
        playPauseButton.text = getString(if (state.isPlaying) R.string.playback_pause else R.string.playback_play)
        playModeButton.text = state.playMode.displayName
        currentTimeText.text = formatTime(state.positionMs)
        durationText.text = formatTime(state.durationMs)
        progressSeekBar.progress = if (state.durationMs > 0L) {
            ((state.positionMs * 1000L) / state.durationMs).toInt().coerceIn(0, 1000)
        } else {
            0
        }
        playerIndicator.text = "${state.currentIndex + 1}/${state.files.size}"
        playerIndicator.visibility =
            if (state.contentReady && state.hasPlaylist && state.showCounter) View.VISIBLE else View.GONE
        // 拍摄地点：仅在首帧渲染后、开启"显示地点"且成功解析出地址时展示。
        if (state.contentReady && state.showLocation && !state.locationText.isNullOrBlank()) {
            locationText.text = state.locationText
            locationText.visibility = View.VISIBLE
        } else {
            locationText.visibility = View.GONE
        }
        // 拍摄时间：首帧渲染后、开启"显示拍摄时间"且能从媒体元数据取到即在左上角展示。
        if (state.contentReady && state.showCaptureTime && !state.captureTimeText.isNullOrBlank()) {
            captureTimeText.text = state.captureTimeText
            captureTimeText.visibility = View.VISIBLE
        } else {
            captureTimeText.visibility = View.GONE
        }
        // 解码后端徽标：仅视频展示，便于现场确认是否走了 LibVLC 软解兑底。
        if (state.isCurrentVideo) {
            decoderBadge.text = getString(
                if (videoPlayerEngine.currentBackend() == VideoPlayerEngine.Backend.LIBVLC) {
                    R.string.playback_decoder_libvlc
                } else {
                    R.string.playback_decoder_media3
                },
            )
            decoderBadge.visibility = View.VISIBLE
        } else {
            decoderBadge.visibility = View.GONE
        }
        if (infoVisible) renderInfoPanel(state)
        refreshPresentationIfNeeded(state)
        bgmCoordinator.onPlaybackStateChanged(state)
        if (quickSelector.visible) syncQuickSelectorToCurrent(state.currentFile)
        // 初始化失败（如播放列表为空）时事件可能在订阅前已丢失，这里按状态兜底提示并退出。
        if (!state.hasPlaylist && !state.isLoading && state.errorMessage != null && !fatalErrorShown) {
            fatalErrorShown = true
            Toast.makeText(this@PlaybackActivity, state.errorMessage, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private suspend fun handleEvent(event: PlaybackUiEvent) {
        when (event) {
            PlaybackUiEvent.Finish -> finish()
            is PlaybackUiEvent.ShowError -> Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            is PlaybackUiEvent.ShowImage -> showImage(event.url, event.file)
            is PlaybackUiEvent.PlayVideo -> playVideo(event.url, event.file)
        }
    }

    private suspend fun playVideo(url: String, file: FileInfo) {
        bgmCoordinator.pause()
        pendingVideo = url to file
        binding.imageDisplay.visibility = View.GONE
        // 播放启动路径不要同步抓取 TextureView 全尺寸画面；4K 帧复制会阻塞主线程并显著拖慢加载。
        stopProgressUpdates()
        videoPlayerEngine.stop()
        // loading 转圈 + 背景垫底（竖屏视频左右黑边处显示背景，与图片一致的三种模式）。
        binding.loadingProgress.visibility = View.VISIBLE
        applyVideoBackground(url)
        // TextureView 必须保持 VISIBLE 才会持有可用的 SurfaceTexture；缓冲时只隐藏画面，
        // 不能设为 GONE，否则下方 isAvailable 会持续为 false，视频永远无法启动。
        binding.videoSurface.visibility = VideoSurfaceState.BUFFERING.visibility
        binding.videoSurface.alpha = VideoSurfaceState.BUFFERING.alpha
        // 新视频先恢复铺满，等 onVideoSizeChanged 回调按真实比例再调整（避免沿用上个视频的尺寸）。
        resetVideoSurfaceToFill()
        // 新视频分辨率未回调前清零，信息面板不应沿用上一个视频的分辨率。
        currentVideoWidth = 0
        currentVideoHeight = 0
        val textureView = binding.videoSurface
        val surfaceTexture = textureView.surfaceTexture
        if (!textureView.isAvailable || surfaceTexture == null || textureView.width == 0) {
            // TextureView 尚未创建/布局完成，等下一帧重试。
            textureView.post { lifecycleScope.launch { playVideo(url, file) } }
            return
        }
        videoOutputSurface?.release()
        val surface = Surface(surfaceTexture).also { videoOutputSurface = it }
        // 两个后端共用 TextureView；窗口尺寸在视频比例回调后还会再次同步。
        videoPlayerEngine.setVideoTextureView(textureView)
        videoPlayerEngine.setVideoSurfaceSize(textureView.width, textureView.height)
        when (val result = videoPlayerEngine.play(url, surface, buildRequestHeaders())) {
            PlaybackResult.Success -> {
                binding.loadingProgress.visibility = View.GONE
                binding.videoSurface.alpha = VideoSurfaceState.RENDERING.alpha
                viewModel.setPlaying(true)
                startProgressUpdates()
                // 视频画面已开始渲染：此刻才显示三个角信息。
                viewModel.notifyContentReady()
                captureVideoThumbnail(file)
            }
            is PlaybackResult.Unsupported -> {
                binding.loadingProgress.visibility = View.GONE
                showCapabilityDialog(result.reason, url, surface)
            }
            is PlaybackResult.Error -> {
                binding.loadingProgress.visibility = View.GONE
                retryOrSkip(result.cause.message ?: "播放失败")
            }
        }
    }

    /**
     * 为视频播放页设置背景（复用图片的三种背景策略：黑色 / 主色调 / 模糊）。
     *
     * 竖屏视频左右、横屏视频上下会留黑边，用视频首帧生成的背景填充这些区域。
     * 抓帧失败（如流不可 seek）时回退为黑色背景。
     */
    private fun preserveVideoFrameAsBackground() {
        if (!binding.videoSurface.isAvailable) return
        // 背景会被模糊/取主色，无需复制完整 4K 帧；限制尺寸避免播放结束切换时阻塞主线程。
        val sourceWidth = binding.videoSurface.width.coerceAtLeast(1)
        val sourceHeight = binding.videoSurface.height.coerceAtLeast(1)
        val scale = minOf(1f, VIDEO_BACKGROUND_MAX_EDGE.toFloat() / maxOf(sourceWidth, sourceHeight))
        val frameWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val frameHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
        val frame = binding.videoSurface.getBitmap(frameWidth, frameHeight) ?: return
        lifecycleScope.launch {
            ImageBackgroundFactory.fromValue(viewModel.uiState.value.imageBackgroundMode)
                .apply(binding.imageBackground, frame)
        }
    }

    private fun applyVideoBackground(url: String) {
        binding.imageBackground.visibility = View.VISIBLE
        val mode = viewModel.uiState.value.imageBackgroundMode
        Glide.with(this)
            .asBitmap()
            .load(url)
            .frame(0)
            .into(
                object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        lifecycleScope.launch {
                            ImageBackgroundFactory.fromValue(mode).apply(binding.imageBackground, resource)
                        }
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        lifecycleScope.launch {
                            ImageBackgroundFactory.fromValue(mode).apply(binding.imageBackground, null)
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {}
                },
            )
    }

    private fun showImage(url: String, file: FileInfo) {
        stopProgressUpdates()
        videoPlayerEngine.stop()
        binding.videoSurface.visibility = View.GONE
        binding.imageBackground.visibility = View.VISIBLE
        binding.imageDisplay.visibility = View.VISIBLE
        binding.fileNameText.text = file.serverFilename.orEmpty()
        Glide.with(this)
            .asBitmap()
            .load(url)
            .into(
                object : BitmapImageViewTarget(binding.imageDisplay) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        super.onResourceReady(resource, transition)
                        currentImageBitmap = resource
                        applyImagePresentation(resource)
                        saveThumbnail(file, resource)
                        // 图片已真正渲染：此刻才显示三个角信息并开始自动切换计时。
                        viewModel.notifyContentReady()
                    }
                },
            )
    }

    private fun saveThumbnail(file: FileInfo, bitmap: Bitmap) {
        val filePath = file.path?.takeIf { it.isNotBlank() } ?: return
        // Glide 管理的 Bitmap 可能在回调返回后被复用，先复制再切到 IO 线程压缩。
        val snapshot = runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val outputDir = File(filesDir, "recent_thumbnails").apply { mkdirs() }
            val name = sha256(filePath).take(32) + ".jpg"
            val output = File(outputDir, name)
            runCatching {
                FileOutputStream(output).use { stream ->
                    check(snapshot.compress(Bitmap.CompressFormat.JPEG, 82, stream)) { "缩略图压缩失败" }
                }
                viewModel.updateHistoryCover(filePath, output.absolutePath)
            }.onFailure {
                Log.w(TAG, "保存最近播放缩略图失败: $filePath", it)
            }.also {
                snapshot.recycle()
            }
        }
    }

    /** 从已经渲染的 TextureView 抓取视频当前帧，避免依赖百度缩略图 URL。 */
    private fun captureVideoThumbnail(file: FileInfo, attempt: Int = 0) {
        if (binding.videoSurface.width <= 0 || binding.videoSurface.height <= 0) {
            Log.w(TAG, "视频缩略图跳过：Surface 无尺寸")
            return
        }
        binding.videoSurface.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val bitmap = binding.videoSurface.bitmap
            if (bitmap != null) {
                Log.d(TAG, "视频缩略图抓帧成功，attempt=$attempt")
                saveThumbnail(file, bitmap)
            } else {
                Log.w(TAG, "视频缩略图抓帧失败，attempt=$attempt")
                if (attempt + 1 < THUMBNAIL_CAPTURE_RETRIES) {
                    captureVideoThumbnail(file, attempt + 1)
                }
            }
        }, if (attempt == 0) 1_000L else THUMBNAIL_CAPTURE_DELAY_MS)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun applyImagePresentation(bitmap: Bitmap) {
        val state = viewModel.uiState.value
        lastPresentationState = state.imageBackgroundMode to state.imageEffect.name
        lifecycleScope.launch {
            ImageBackgroundFactory.fromValue(state.imageBackgroundMode)
                .apply(binding.imageBackground, bitmap)
            ImageEffectFactory.resolve(state.imageEffect).apply(binding.imageDisplay)
        }
    }

    /** 设置页返回后立即把新背景/特效应用到当前仍在显示的媒体，不必切换到下一项。 */
    private fun refreshPresentationIfNeeded(state: PlaybackUiState) {
        val presentationState = state.imageBackgroundMode to state.imageEffect.name
        if (presentationState == lastPresentationState) return
        when {
            state.isCurrentImage -> currentImageBitmap?.let { applyImagePresentation(it) }
            state.isCurrentVideo -> {
                val frame = binding.videoSurface.bitmap
                if (frame != null) {
                    lastPresentationState = presentationState
                    lifecycleScope.launch {
                        ImageBackgroundFactory.fromValue(state.imageBackgroundMode)
                            .apply(binding.imageBackground, frame)
                    }
                }
            }
        }
    }

    private fun showCapabilityDialog(reason: UnsupportedReason, url: String, surface: Surface) {
        val message = when (reason) {
            UnsupportedReason.HEVC_10BIT -> "当前视频可能为 HEVC 10-bit，部分 TV 硬件解码可能失败。FFmpeg 软解扩展尚未集成，可继续用 Media3 硬解尝试。"
            UnsupportedReason.HEVC_4K -> "当前视频可能为 HEVC 4K，部分 TV 设备可能卡顿或无法硬解。可继续用 Media3 硬解尝试。"
            UnsupportedReason.DOLBY_VISION -> "当前视频是 Dolby Vision，当前设备/模拟器可能无法解码，会出现 MediaCodecVideoRenderer 错误。FFmpeg 软解扩展尚未集成，建议跳过此视频。"
            UnsupportedReason.NO_HARDWARE_DECODER -> "设备未检测到可用硬件解码器，且 FFmpeg 软解扩展尚未集成。"
        }
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.playback_capability_title)
            .setMessage(message)
            .setNegativeButton(R.string.playback_skip) { _, _ -> viewModel.playNext() }
        if (reason == UnsupportedReason.NO_HARDWARE_DECODER || reason == UnsupportedReason.DOLBY_VISION) {
            builder.setPositiveButton(R.string.playback_close) { _, _ -> viewModel.playNext() }
        } else {
            builder.setPositiveButton(R.string.playback_continue) { _, _ ->
                lifecycleScope.launch {
                    val forced = (videoPlayerEngine as? Media3VideoPlayerEngine)?.playForced(url, surface, buildRequestHeaders())
                    if (forced == PlaybackResult.Success) {
                        viewModel.setPlaying(true)
                        startProgressUpdates()
                    } else {
                        retryOrSkip("强制播放失败")
                    }
                }
            }
        }
        builder.show()
    }

    private fun togglePlayback() {
        if (viewModel.uiState.value.isPlaying) {
            videoPlayerEngine.pause()
            viewModel.setPlaying(false)
        } else {
            videoPlayerEngine.resume()
            viewModel.setPlaying(true)
        }
        showControls()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = lifecycleScope.launch {
            while (isActive) {
                viewModel.updateProgress(videoPlayerEngine.currentPosition(), videoPlayerEngine.duration())
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlPanel.visibility = View.VISIBLE
        binding.playPauseButton.requestFocus()
        scheduleAutoHide()
    }

    private fun hideControls() {
        controlsVisible = false
        binding.controlPanel.visibility = View.GONE
        binding.playbackRoot.requestFocus()
    }

    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        autoHideJob = lifecycleScope.launch {
            delay(CONTROL_AUTO_HIDE_MS)
            hideControls()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            // 设置/菜单键：播放期间也可直接进入设置；返回后恢复原播放状态。
            KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_M -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            // 专用信息键保留文件信息面板（含当前解码后端）。
            KeyEvent.KEYCODE_INFO -> {
                toggleInfoPanel()
                true
            }
            // 遥控器媒体「上一个 / 下一个」键：无论当前是视频还是图片，直接切换整个媒体项。
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                viewModel.playPrevious()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                viewModel.playNext()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when {
                    quickSelector.visible -> {
                        quickSelector.confirmSelection()
                        true
                    }
                    !controlsVisible -> { showControls(); true }
                    else -> { togglePlayback(); true }
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                when {
                    quickSelector.visible -> super.dispatchKeyEvent(event)
                    !controlsVisible -> { showQuickSelector(); true }
                    else -> super.dispatchKeyEvent(event)
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                when {
                    quickSelector.visible -> { hideQuickSelector(); true }
                    !controlsVisible -> { showControls(); true }
                    else -> super.dispatchKeyEvent(event)
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                when {
                    quickSelector.visible -> super.dispatchKeyEvent(event)
                    controlsVisible -> super.dispatchKeyEvent(event)
                    else -> { seekOrPrevious(); true }
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                when {
                    quickSelector.visible -> super.dispatchKeyEvent(event)
                    controlsVisible -> super.dispatchKeyEvent(event)
                    else -> { seekOrNext(); true }
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                when {
                    quickSelector.visible -> { hideQuickSelector(); true }
                    infoVisible -> { hideInfoPanel(); true }
                    controlsVisible -> { hideControls(); true }
                    else -> super.dispatchKeyEvent(event)
                }
            }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private var infoVisible = false
    private var currentVideoWidth = 0
    private var currentVideoHeight = 0

    private fun toggleInfoPanel() {
        if (infoVisible) hideInfoPanel() else showInfoPanel()
    }

    private fun showInfoPanel() {
        infoVisible = true
        renderInfoPanel(viewModel.uiState.value)
        binding.infoPanel.visibility = View.VISIBLE
    }

    private fun hideInfoPanel() {
        infoVisible = false
        binding.infoPanel.visibility = View.GONE
    }

    private fun renderInfoPanel(state: PlaybackUiState) {
        val file = state.currentFile ?: return
        binding.infoFileName.text = file.serverFilename.orEmpty()
        binding.infoFilePath.text = file.path.orEmpty()
        val parts = mutableListOf<String>()
        parts += getString(
            if (state.isCurrentVideo) R.string.playback_info_type_video else R.string.playback_info_type_image,
        )
        if (file.size > 0) parts += android.text.format.Formatter.formatShortFileSize(this, file.size)
        if (state.isCurrentVideo) {
            if (currentVideoWidth > 0 && currentVideoHeight > 0) {
                parts += "${currentVideoWidth}×${currentVideoHeight}"
            }
            if (state.durationMs > 0) parts += formatTime(state.durationMs)
            parts += getString(
                if (videoPlayerEngine.currentBackend() == VideoPlayerEngine.Backend.LIBVLC) {
                    R.string.playback_decoder_libvlc
                } else {
                    R.string.playback_decoder_media3
                },
            )
        }
        binding.infoMeta.text = parts.joinToString("  ·  ")
    }

    private fun seekOrPrevious() {
        val state = viewModel.uiState.value
        if (state.isCurrentVideo) {
            val target = (videoPlayerEngine.currentPosition() - SEEK_STEP_MS).coerceAtLeast(0L)
            videoPlayerEngine.seekTo(target)
            viewModel.updateProgress(target, videoPlayerEngine.duration())
        } else {
            viewModel.playPrevious()
        }
    }

    private fun seekOrNext() {
        val state = viewModel.uiState.value
        if (state.isCurrentVideo) {
            val duration = videoPlayerEngine.duration()
            val target = (videoPlayerEngine.currentPosition() + SEEK_STEP_MS).coerceAtMost(duration.takeIf { it > 0 } ?: Long.MAX_VALUE)
            videoPlayerEngine.seekTo(target)
            viewModel.updateProgress(target, duration)
        } else {
            viewModel.playNext()
        }
    }

    override fun onReady(durationMs: Long) {
        viewModel.updateProgress(videoPlayerEngine.currentPosition(), durationMs)
    }

    /**
     * 按视频真实宽高比调整 [videoSurface] 尺寸（fit-center）：
     * TextureView 会把画面铺满自身，需据此缩放到容器内的正确比例，
     * 横屏视频占满水平边、竖屏视频占满垂直边。回调可能在非主线程，切回主线程更新布局。
     */
    override fun onVideoSizeChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        runOnUiThread {
            // Media3 已应用视频旋转元数据；LibVLC 当前实机也未观察到方向异常。
            // 两个后端均不额外旋转 TextureView，只按其上报的最终显示宽高布局。
            currentVideoWidth = width
            currentVideoHeight = height
            resizeVideoSurface(width, height)
            if (infoVisible) renderInfoPanel(viewModel.uiState.value)
        }
    }

    private fun resetVideoSurfaceToFill() {
        binding.videoSurface.rotation = 0f
        val params = binding.videoSurface.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        params.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        params.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        params.gravity = android.view.Gravity.CENTER
        binding.videoSurface.layoutParams = params
    }

    private fun resizeVideoSurface(videoWidth: Int, videoHeight: Int, outputRotation: Int = 0) {
        val container = binding.playbackRoot
        val target = VideoLayoutCalculator.fitCenterLayout(
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            containerWidth = container.width,
            containerHeight = container.height,
            rotationDegrees = outputRotation,
        ) ?: return
        val params = binding.videoSurface.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        params.width = target.surfaceSize.width
        params.height = target.surfaceSize.height
        params.gravity = android.view.Gravity.CENTER
        binding.videoSurface.layoutParams = params
        // 显式使用新布局尺寸的中心点，避免动态切换横竖屏后沿用旧 pivot 导致偏移/裁剪。
        binding.videoSurface.pivotX = target.surfaceSize.width / 2f
        binding.videoSurface.pivotY = target.surfaceSize.height / 2f
        binding.videoSurface.rotation = outputRotation.toFloat()
        // LibVLC 的 vout 窗口必须跟随 TextureView 旋转前的实际布局尺寸。
        videoPlayerEngine.setVideoSurfaceSize(target.surfaceSize.width, target.surfaceSize.height)
    }

    private fun setupQuickSelector() {
        QuickSelectorController.setupRecyclerView(binding.quickSelectorList)
        binding.quickSelectorList.adapter = selectAdapter
        quickSelector = QuickSelectorController(
            adapter = selectAdapter,
            recyclerView = binding.quickSelectorList,
            thumbnailProvider = thumbnailProvider,
            scope = lifecycleScope,
            focusHost = binding.playbackRoot,
        )
    }

    /** 上键呼出快速选播列表：倒序展示当前播放列表，浮在播放内容上方。 */
    private fun showQuickSelector() {
        val state = viewModel.uiState.value
        quickSelector.show(state.files, state.currentFile) { selectedFile ->
            val latestFiles = viewModel.uiState.value.files
            val selectedKey = fileKey(selectedFile)
            val originalIndex = latestFiles.indexOfFirst { fileKey(it) == selectedKey }
            if (originalIndex >= 0) {
                viewModel.playFromIndex(originalIndex)
            }
        }
    }

    private fun syncQuickSelectorToCurrent(currentFile: FileInfo?) {
        val state = viewModel.uiState.value
        quickSelector.syncCurrent(state.files, currentFile)
    }

    private fun fileKey(file: FileInfo): String =
        if (file.fsId != 0L) "fs:${file.fsId}" else "path:${file.path.orEmpty()}"

    private fun hideQuickSelector() {
        quickSelector.hide()
    }

    override fun onEnded() {
        // 切歌前立即隐藏 TextureView；否则结束瞬间最后一帧可能覆盖模糊背景并被拉伸到全屏。
        preserveVideoFrameAsBackground()
        binding.videoSurface.visibility = View.GONE
        binding.loadingProgress.visibility = View.VISIBLE
        viewModel.playNext()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        viewModel.setPlaying(isPlaying)
    }

    override fun onError(error: androidx.media3.common.PlaybackException) {
        retryOrSkip(error.message ?: "播放错误")
    }

    override fun onUnsupported(reason: UnsupportedReason) {
        retryOrSkip(
            when (reason) {
                UnsupportedReason.DOLBY_VISION -> "当前视频为 Dolby Vision，设备不支持，已跳过"
                UnsupportedReason.HEVC_10BIT -> "当前视频为 HEVC 10-bit，设备不支持，已跳过"
                UnsupportedReason.HEVC_4K -> "当前视频为 HEVC 4K，设备不支持，已跳过"
                UnsupportedReason.NO_HARDWARE_DECODER -> "设备未检测到可用硬件解码器，已跳过"
            },
        )
    }

    private fun retryOrSkip(message: String) {
        val path = viewModel.uiState.value.currentFile?.path
        if (!path.isNullOrBlank() && retriedFilePath != path) {
            retriedFilePath = path
            Log.w(TAG, "首播失败，重试当前文件一次: $path, reason=$message")
            Toast.makeText(this, "播放初始化失败，正在重试…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                delay(FIRST_PLAY_RETRY_DELAY_MS)
                if (!isFinishing && !isDestroyed && viewModel.uiState.value.currentFile?.path == path) {
                    viewModel.retryCurrent()
                }
            }
        } else {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            viewModel.playNext()
        }
    }

    private fun buildRequestHeaders(): Map<String, String> = mapOf("User-Agent" to "pan.baidu.com")

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0L)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        bgmCoordinator.onForegroundChanged(true)
        hideSystemBars()
        val state = viewModel.uiState.value
        refreshPresentationIfNeeded(state)
        bgmCoordinator.onPlaybackStateChanged(state)
        if (resumePlaybackOnReturn && state.isCurrentVideo && state.contentReady) {
            videoPlayerEngine.resume()
        }
        resumePlaybackOnReturn = false
    }

    override fun onPause() {
        resumePlaybackOnReturn = viewModel.uiState.value.isCurrentVideo && videoPlayerEngine.isPlaying()
        bgmCoordinator.onForegroundChanged(false)
        bgmCoordinator.pause()
        videoPlayerEngine.pause()
        super.onPause()
    }

    override fun onDestroy() {
        stopProgressUpdates()
        autoHideJob?.cancel()
        // 解绑 listener，避免引擎（@Singleton，生命周期长于本 Activity）回调到已销毁的 UI。
        when (val engine = videoPlayerEngine) {
            is HybridVideoPlayerEngine -> engine.listener = null
            is Media3VideoPlayerEngine -> engine.listener = null
        }
        // 只 stop 不 release：引擎为进程级单例（复用底层 LibVLC 原生对象），
        // release() 会销毁原生 LibVLC，Activity 重建后再用会崩溃/触发 finalizer 断言。
        videoPlayerEngine.stop()
        bgmCoordinator.release()
        videoOutputSurface?.release()
        videoOutputSurface = null
        super.onDestroy()
    }
}
