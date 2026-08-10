package com.baidu.tv.player.kt.ui.playback

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.PixelCopy
import android.view.Surface
import android.util.Log
import android.view.SurfaceView
import android.view.View
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
import com.baidu.tv.player.kt.player.HybridVideoPlayerEngine
import com.baidu.tv.player.kt.player.Media3VideoPlayerEngine
import com.baidu.tv.player.kt.player.PlaybackResult
import com.baidu.tv.player.kt.player.UnsupportedReason
import com.baidu.tv.player.kt.player.VideoPlayerEngine
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject

private const val CONTROL_AUTO_HIDE_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
private const val PROGRESS_INTERVAL_MS = 1_000L
private const val THUMBNAIL_CAPTURE_RETRIES = 5
private const val THUMBNAIL_CAPTURE_DELAY_MS = 800L
private const val TAG = "PlaybackActivity"

/**
 * 播放页（Phase 5）：Media3 视频播放 + 图片播放/特效/背景。
 *
 * VLC 已完全移除：本 Activity 仅依赖 [VideoPlayerEngine] / [Media3VideoPlayerEngine]。
 */
@AndroidEntryPoint
class PlaybackActivity : FragmentActivity(), Media3VideoPlayerEngine.Listener {

    private lateinit var binding: ActivityPlaybackBinding
    private val viewModel: PlaybackViewModel by viewModels()

    @Inject lateinit var videoPlayerEngine: VideoPlayerEngine

    private var controlsVisible = false
    private var autoHideJob: Job? = null
    private var progressJob: Job? = null
    private var pendingVideo: Pair<String, FileInfo>? = null
    private var fatalErrorShown = false
    private var resumePlaybackOnReturn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 裸 SurfaceView 默认 Surface 在 Window 之下：会把整个 SurfaceView bounds 挖穿露出 Surface，
        // 导致 imageBackground 被遮住（黑边区看不到背景）。提升为 media overlay 后，Surface 合成到
        // Window 之上、仅占已按视频比例缩放的 SurfaceView bounds，黑边区即可露出背后的 imageBackground。
        binding.videoSurface.setZOrderMediaOverlay(true)
        hideSystemBars()
        setupButtons()
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
        pendingVideo = url to file
        binding.imageDisplay.visibility = View.GONE
        // 切换视频时先停止上一个引擎并清除其残留画面，缓冲期间不应停留在上一帧。
        stopProgressUpdates()
        videoPlayerEngine.stop()
        // loading 转圈 + 背景垫底（竖屏视频左右黑边处显示背景，与图片一致的三种模式）。
        binding.loadingProgress.visibility = View.VISIBLE
        applyVideoBackground(url)
        // SurfaceView 先隐藏再显示以重建底层 Surface，清空上一个视频遗留的最后一帧。
        binding.videoSurface.visibility = View.GONE
        binding.videoSurface.visibility = View.VISIBLE
        // 新视频先恢复铺满，等 onVideoSizeChanged 回调按真实比例再调整（避免沿用上个视频的尺寸）。
        resetVideoSurfaceToFill()
        // 新视频分辨率未回调前清零，信息面板不应沿用上一个视频的分辨率。
        currentVideoWidth = 0
        currentVideoHeight = 0
        val surfaceView = binding.videoSurface
        val surface = surfaceView.holder.surface
        if (surface == null || !surface.isValid || surfaceView.width == 0) {
            // Surface 尚未创建/布局完成，等下一帧重试。
            surfaceView.post { lifecycleScope.launch { playVideo(url, file) } }
            return
        }
        // LibVLC 需要 SurfaceView 引用（官方 setVideoView 路径）+ 窗口尺寸，否则黑屏。
        videoPlayerEngine.setVideoSurfaceView(surfaceView)
        videoPlayerEngine.setVideoSurfaceSize(surfaceView.width, surfaceView.height)
        when (val result = videoPlayerEngine.play(url, surface, buildRequestHeaders())) {
            PlaybackResult.Success -> {
                binding.loadingProgress.visibility = View.GONE
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
                showToastAndSkip(result.cause.message ?: "播放失败")
            }
        }
    }

    /**
     * 为视频播放页设置背景（复用图片的三种背景策略：黑色 / 主色调 / 模糊）。
     *
     * 竖屏视频左右、横屏视频上下会留黑边，用视频首帧生成的背景填充这些区域。
     * 抓帧失败（如流不可 seek）时回退为黑色背景。
     */
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

    /** 从已经渲染的 SurfaceView 抓取视频当前帧，避免依赖百度缩略图 URL。 */
    private fun captureVideoThumbnail(file: FileInfo, attempt: Int = 0) {
        if (binding.videoSurface.width <= 0 || binding.videoSurface.height <= 0) {
            Log.w(TAG, "视频缩略图跳过：Surface 无尺寸")
            return
        }
        binding.videoSurface.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            val bitmap = Bitmap.createBitmap(
                binding.videoSurface.width,
                binding.videoSurface.height,
                Bitmap.Config.ARGB_8888,
            )
            PixelCopy.request(
                binding.videoSurface,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        Log.d(TAG, "视频缩略图抓帧成功，attempt=$attempt")
                        saveThumbnail(file, bitmap)
                    } else {
                        bitmap.recycle()
                        Log.w(TAG, "视频缩略图抓帧失败，result=$result, attempt=$attempt")
                        if (attempt + 1 < THUMBNAIL_CAPTURE_RETRIES) {
                            captureVideoThumbnail(file, attempt + 1)
                        }
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper()),
            )
        }, if (attempt == 0) 1_000L else THUMBNAIL_CAPTURE_DELAY_MS)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun applyImagePresentation(bitmap: Bitmap) {
        val state = viewModel.uiState.value
        lifecycleScope.launch {
            ImageBackgroundFactory.fromValue(state.imageBackgroundMode)
                .apply(binding.imageBackground, bitmap)
            ImageEffectFactory.resolve(state.imageEffect).apply(binding.imageDisplay)
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
                        showToastAndSkip("强制播放失败")
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
                if (!controlsVisible) showControls() else togglePlayback()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                showControls()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (controlsVisible) super.dispatchKeyEvent(event) else {
                    seekOrPrevious()
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (controlsVisible) super.dispatchKeyEvent(event) else {
                    seekOrNext()
                    true
                }
            }
            KeyEvent.KEYCODE_BACK -> {
                when {
                    infoVisible -> {
                        hideInfoPanel()
                        true
                    }
                    controlsVisible -> {
                        hideControls()
                        true
                    }
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
     * 裸 SurfaceView 会把画面拉伸铺满自身，需据此把 SurfaceView 缩放到容器内的正确比例，
     * 横屏视频占满水平边、竖屏视频占满垂直边。回调可能在非主线程，切回主线程更新布局。
     */
    override fun onVideoSizeChanged(width: Int, height: Int, rotationDegrees: Int) {
        if (width <= 0 || height <= 0) return
        runOnUiThread {
            val quarterTurn = rotationDegrees == 90 || rotationDegrees == 270
            currentVideoWidth = if (quarterTurn) height else width
            currentVideoHeight = if (quarterTurn) width else height
            resizeVideoSurface(width, height, rotationDegrees)
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

    private fun resizeVideoSurface(videoWidth: Int, videoHeight: Int, rotationDegrees: Int) {
        val container = binding.playbackRoot
        val containerWidth = container.width
        val containerHeight = container.height
        if (containerWidth == 0 || containerHeight == 0) return
        val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
        val quarterTurn = normalizedRotation == 90 || normalizedRotation == 270
        val visualWidth = if (quarterTurn) videoHeight else videoWidth
        val visualHeight = if (quarterTurn) videoWidth else videoHeight
        val videoRatio = visualWidth.toFloat() / visualHeight
        val containerRatio = containerWidth.toFloat() / containerHeight
        val (targetVisualWidth, targetVisualHeight) = if (videoRatio > containerRatio) {
            containerWidth to (containerWidth / videoRatio).toInt()
        } else {
            (containerHeight * videoRatio).toInt() to containerHeight
        }
        // SurfaceView 旋转 90/270° 后视觉包围盒宽高会交换，因此布局尺寸需要反向设置。
        val targetLayoutWidth = if (quarterTurn) targetVisualHeight else targetVisualWidth
        val targetLayoutHeight = if (quarterTurn) targetVisualWidth else targetVisualHeight
        val params = binding.videoSurface.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        params.width = targetLayoutWidth
        params.height = targetLayoutHeight
        params.gravity = android.view.Gravity.CENTER
        binding.videoSurface.layoutParams = params
        binding.videoSurface.rotation = normalizedRotation.toFloat()
    }

    override fun onEnded() {
        viewModel.playNext()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        viewModel.setPlaying(isPlaying)
    }

    override fun onError(error: androidx.media3.common.PlaybackException) {
        showToastAndSkip(error.message ?: "播放错误")
    }

    override fun onUnsupported(reason: UnsupportedReason) {
        showToastAndSkip(
            when (reason) {
                UnsupportedReason.DOLBY_VISION -> "当前视频为 Dolby Vision，设备不支持，已跳过"
                UnsupportedReason.HEVC_10BIT -> "当前视频为 HEVC 10-bit，设备不支持，已跳过"
                UnsupportedReason.HEVC_4K -> "当前视频为 HEVC 4K，设备不支持，已跳过"
                UnsupportedReason.NO_HARDWARE_DECODER -> "设备未检测到可用硬件解码器，已跳过"
            },
        )
    }

    private fun showToastAndSkip(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        viewModel.playNext()
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
        hideSystemBars()
        val state = viewModel.uiState.value
        if (resumePlaybackOnReturn && state.isCurrentVideo && state.contentReady) {
            videoPlayerEngine.resume()
        }
        resumePlaybackOnReturn = false
    }

    override fun onPause() {
        resumePlaybackOnReturn = viewModel.uiState.value.isCurrentVideo && videoPlayerEngine.isPlaying()
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
        super.onDestroy()
    }
}
