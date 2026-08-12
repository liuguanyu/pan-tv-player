package com.baidu.tv.player.kt.ui.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baidu.tv.player.kt.location.LocationExtractionService
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEFAULT_IMAGE_DISPLAY_MS = 8_000L
private const val DEFAULT_TRANSITION_MS = 1_000L
private const val TAG = "PlaybackViewModel"

/** 播放页 UI 状态。 */
data class PlaybackUiState(
    val playlistId: String = "",
    val files: List<FileInfo> = emptyList(),
    val currentIndex: Int = 0,
    val currentFile: FileInfo? = null,
    val mediaType: Int = MediaType.ALL.code,
    val folderPath: String = "",
    val folderName: String = "",
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val imageEffect: ImageEffect = ImageEffect.FADE,
    val imageBackgroundMode: Int = 0,
    val imageDisplayDurationMs: Long = DEFAULT_IMAGE_DISPLAY_MS,
    val transitionDurationMs: Long = DEFAULT_TRANSITION_MS,
    val preparedUrl: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val errorMessage: String? = null,
    val showLocation: Boolean = true,
    val locationText: String? = null,
    val captureTimeText: String? = null,
    val showCounter: Boolean = true,
    val showCaptureTime: Boolean = true,
    /** 当前播放上下文来源：数据库播放列表 id（可空）。用于写入文件级最近播放记录。 */
    val sourcePlaylistId: Long? = null,
    /** 当前播放上下文来源：云盘目录路径（可空）。用于写入文件级最近播放记录。 */
    val sourceFolderPath: String? = null,
    /**
     * 当前媒体首帧是否已真正渲染出来。
     * 切换媒体时置 false，图片 Glide 加载完成 / 视频播放成功后由 UI 回调置 true。
     * 三个角辅助信息（拍摄时间 / 地点 / 计数）与图片自动切换计时都在此为 true 后才生效，
     * 避免在加载/缓冲阶段就提前显示信息或提前开始倒计时。
     */
    val contentReady: Boolean = false,
) {
    val hasPlaylist: Boolean get() = files.isNotEmpty()
    val isCurrentVideo: Boolean get() = currentFile?.isVideo() == true
    val isCurrentImage: Boolean get() = currentFile?.isImage() == true
}

/** 播放页一次性事件。 */
sealed interface PlaybackUiEvent {
    data class PlayVideo(val url: String, val file: FileInfo) : PlaybackUiEvent
    data class ShowImage(val url: String, val file: FileInfo) : PlaybackUiEvent
    data class ShowError(val message: String) : PlaybackUiEvent
    data object Finish : PlaybackUiEvent
}

/**
 * 播放页 ViewModel（Phase 12：纯编排）。
 *
 * 职责仅限：
 * - 持有 [PlaybackUiState] 与 [PlaybackUiEvent]。
 * - 接收用户意图并委托协调器执行。
 * - 订阅设置流并同步到 UiState。
 *
 * 重逻辑委托：
 * - 会话构建 → [PlaybackSessionFactory]
 * - 队列导航 → [PlaybackQueueNavigator]
 * - URL 解析 / 预加载 / generation 并发 → [MediaPreparationCoordinator]
 * - 历史映射 / 写入降级 → [PlaybackHistoryRecorder]（由 Coordinator 调用）
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val historyRepository: PlaybackHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val locationExtractionService: LocationExtractionService,
    private val sessionFactory: PlaybackSessionFactory,
    private val queueNavigator: PlaybackQueueNavigator,
    private val preparationCoordinator: MediaPreparationCoordinator,
) : ViewModel(), PrepareCallback {

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaybackUiEvent> = _events.asSharedFlow()

    private var locationJob: Job? = null
    private var imageAutoNextJob: Job? = null

    init {
        observeSettings()
    }

    /** 订阅设置仓库，让设置页的修改（播放模式/特效/背景/时长）实时作用于播放页。 */
    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.playMode.collect { mode ->
                queueNavigator.clearRandomQueue()
                _uiState.update { it.copy(playMode = mode) }
            }
        }
        viewModelScope.launch {
            settingsRepository.imageEffect.collect { effect ->
                _uiState.update { it.copy(imageEffect = effect) }
            }
        }
        viewModelScope.launch {
            settingsRepository.backgroundMode.collect { mode ->
                _uiState.update { it.copy(imageBackgroundMode = mode.value) }
            }
        }
        viewModelScope.launch {
            settingsRepository.imageDisplayDurationMs.collect { ms ->
                _uiState.update { it.copy(imageDisplayDurationMs = ms.toLong()) }
                if (_uiState.value.isCurrentImage && _uiState.value.isPlaying && _uiState.value.contentReady) {
                    scheduleImageAutoNext()
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.imageTransitionDurationMs.collect { ms ->
                _uiState.update { it.copy(transitionDurationMs = ms.toLong()) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showLocation.collect { enabled ->
                _uiState.update { it.copy(showLocation = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showCounter.collect { enabled ->
                _uiState.update { it.copy(showCounter = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.showCaptureTime.collect { enabled ->
                _uiState.update { it.copy(showCaptureTime = enabled) }
            }
        }

    }

    fun initialize(
        playlistId: String,
        mediaType: Int,
        folderPath: String,
        startIndex: Int,
        selectedPath: String? = null,
    ) {
        if (_uiState.value.files.isNotEmpty()) return
        val session = try {
            sessionFactory.fromDirectoryCache(playlistId, mediaType, folderPath, startIndex, selectedPath)
        } catch (e: PlaybackSessionException) {
            emitError(e.message ?: "播放列表为空")
            return
        }
        applySession(session)
    }

    /**
     * 从 Room 数据库播放列表初始化（主页播放列表卡片入口，extras 传 playlistDatabaseId）。
     *
     * @param startFsId 可选：从指定 fs_id 的文件开始播放（点击"最近播放"进入时定位到该文件）。
     */
    fun initializeFromDatabasePlaylist(playlistDbId: Long, startFsId: Long? = null) {
        if (_uiState.value.files.isNotEmpty()) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val session = try {
                sessionFactory.fromDatabasePlaylist(playlistDbId, startFsId)
            } catch (e: PlaybackSessionException) {
                emitError(e.message ?: "播放列表不存在")
                return@launch
            }
            applySession(session)
        }
    }

    /** 从播放历史初始化（主页"最近播放"入口）。将所有最近播放记录作为播放列表，定位到被点击的文件。 */
    fun initializeFromHistory(historyId: Long) {
        if (_uiState.value.files.isNotEmpty()) return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val session = try {
                sessionFactory.fromRecentHistory(historyId)
            } catch (e: PlaybackSessionException) {
                emitError(e.message ?: "播放历史为空")
                return@launch
            }
            applySession(session)
        }
    }

    /**
     * 将 [PlaybackSession] 应用到 UiState 并触发当前媒体准备（Phase 7）。
     *
     * 三条 initialize 入口统一调用此方法，消除重复的 UiState 组装代码。
     */
    private fun applySession(session: PlaybackSession) {
        val files = session.items
        val safeIndex = session.startIndex
        _uiState.update {
            it.copy(
                playlistId = session.playlistId,
                files = files,
                currentIndex = safeIndex,
                currentFile = files[safeIndex],
                mediaType = session.mediaType,
                folderPath = session.folderPath,
                folderName = session.title,
                sourcePlaylistId = session.sourcePlaylistId,
                sourceFolderPath = session.sourceFolderPath,
                isLoading = true,
                errorMessage = null,
            )
        }
        prepareAndEmitCurrent()
    }

    fun togglePlayPause() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
    }

    fun setPlaying(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
    }

    fun updateProgress(positionMs: Long, durationMs: Long) {
        _uiState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L), durationMs = durationMs.coerceAtLeast(0L)) }
    }

    fun playNext() {
        val state = _uiState.value
        val nextIndex = queueNavigator.nextIndex(state.files.size, state.currentIndex, state.playMode, forward = true) ?: return
        switchToIndex(nextIndex)
    }

    fun playPrevious() {
        val state = _uiState.value
        val prevIndex = queueNavigator.nextIndex(state.files.size, state.currentIndex, state.playMode, forward = false) ?: return
        switchToIndex(prevIndex)
    }

    /** 从指定索引开始播放，后续按当前播放模式继续。适用于快速选播列表/跳播。 */
    fun playFromIndex(index: Int) {
        switchToIndex(index)
    }

    fun cyclePlayMode() {
        val next = when (_uiState.value.playMode) {
            PlayMode.SEQUENTIAL -> PlayMode.RANDOM
            PlayMode.RANDOM -> PlayMode.SINGLE
            PlayMode.SINGLE -> PlayMode.REVERSE
            PlayMode.REVERSE -> PlayMode.SEQUENTIAL
        }
        queueNavigator.clearRandomQueue()
        _uiState.update { it.copy(playMode = next) }
        // 播放页内切换的模式同步持久化，与设置页保持一致。
        settingsRepository.setPlayMode(next)
        preloadNextFile()
    }

    fun setImageEffect(effect: ImageEffect) {
        _uiState.update { it.copy(imageEffect = effect) }
    }

    fun setImageBackgroundMode(mode: Int) {
        _uiState.update { it.copy(imageBackgroundMode = mode) }
    }

    fun retryCurrent() {
        val file = _uiState.value.currentFile ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val gen = preparationCoordinator.nextGeneration()
        preparationCoordinator.prepare(file, gen, viewModelScope, _uiState, this)
    }

    fun preloadNextFile() {
        val state = _uiState.value
        val nextIndex = queueNavigator.nextIndex(state.files.size, state.currentIndex, state.playMode, forward = true) ?: return
        val file = state.files.getOrNull(nextIndex) ?: return
        preparationCoordinator.preloadNextFile(viewModelScope, file)
    }

    internal suspend fun awaitPreloadForTest() {
        preparationCoordinator.awaitPreloadForTest()
    }

    private fun switchToIndex(index: Int) {
        val files = _uiState.value.files
        if (index !in files.indices) return
        if (locationJob?.isActive == true) {
            Log.d(TAG, "切换播放项，取消上一媒体的位置与拍摄时间探测")
        }
        locationJob?.cancel()
        locationJob = null
        _uiState.update {
            it.copy(
                currentIndex = index,
                currentFile = files[index],
                preparedUrl = null,
                positionMs = 0L,
                durationMs = 0L,
                isLoading = true,
                errorMessage = null,
                locationText = null,
                captureTimeText = null,
                // 切换到新媒体：首帧尚未渲染，先隐藏三个角信息并停止计时，待就绪回调再置 true。
                contentReady = false,
            )
        }
        prepareAndEmitCurrent()
    }

    private fun prepareAndEmitCurrent() {
        val file = _uiState.value.currentFile ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val gen = preparationCoordinator.nextGeneration()
        preparationCoordinator.prepare(file, gen, viewModelScope, _uiState, this)
    }

    // ------------------------------------------------------------------
    // PrepareCallback 实现：由 MediaPreparationCoordinator 回调
    // ------------------------------------------------------------------

    override suspend fun onPrepareSuccess(file: FileInfo, url: String) {
        _uiState.update { it.copy(preparedUrl = url, isLoading = false, isPlaying = true) }
        if (file.isVideo()) {
            _events.emit(PlaybackUiEvent.PlayVideo(url, file))
            extractLocationFor(url, isVideo = true)
        } else if (file.isImage()) {
            _events.emit(PlaybackUiEvent.ShowImage(url, file))
            extractLocationFor(url, isVideo = false)
            // 图片自动切换计时改由 notifyContentReady()（图片真正渲染后）启动，此处不再预启。
        } else {
            _events.emit(PlaybackUiEvent.ShowError("不支持的文件类型: ${file.serverFilename.orEmpty()}"))
        }
        preloadNextFile()
    }

    override suspend fun onPrepareFailure(file: FileInfo, message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message, isPlaying = false) }
        _events.emit(PlaybackUiEvent.ShowError(message))
    }

    /**
     * 异步提取当前媒体拍摄地点（EXIF GPS → 逆地理编码），结果写入 uiState.locationText。
     * 切换文件时取消上一个提取任务并先清空旧地点；失败静默（locationText 保持 null）。
     */
    private fun extractLocationFor(url: String, isVideo: Boolean) {
        if (locationJob?.isActive == true) {
            Log.d(TAG, "新媒体已就绪，取消上一媒体探测")
        }
        locationJob?.cancel()
        Log.d(TAG, "启动媒体信息探测，类型=${if (isVideo) "video" else "image"}")
        // 切换文件先清空旧的地点与拍摄时间，避免残留上一媒体信息。
        _uiState.update { it.copy(locationText = null, captureTimeText = null) }
        locationJob = viewModelScope.launch {
            // 地点：受"显示地点"设置控制。
            launch {
                if (!_uiState.value.showLocation) return@launch
                val location = try {
                    locationExtractionService.extractLocation(url, isVideo)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (location != null) {
                    _uiState.update {
                        if (it.preparedUrl == url) it.copy(locationText = location) else it
                    }
                }
            }
            // 拍摄时间：独立于地点，能取到即显示（与地点并行提取，互不阻塞）。
            launch {
                val time = try {
                    locationExtractionService.extractCaptureTime(url, isVideo)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                if (!time.isNullOrBlank()) {
                    _uiState.update {
                        if (it.preparedUrl == url) it.copy(captureTimeText = time) else it
                    }
                }
            }
        }
    }

    private fun scheduleImageAutoNext() {
        imageAutoNextJob?.cancel()
        imageAutoNextJob = viewModelScope.launch {
            delay(_uiState.value.imageDisplayDurationMs)
            if (_uiState.value.isCurrentImage && _uiState.value.isPlaying) {
                playNext()
            }
        }
    }

    /** 播放页成功渲染后，将本地截图写回当前历史记录。 */
    fun updateHistoryCover(filePath: String, coverPath: String) {
        if (filePath.isBlank()) return
        viewModelScope.launch {
            historyRepository.updateCover(filePath, coverPath)
        }
    }

    /**
     * 由 UI 层在媒体首帧真正渲染完成后调用（图片 Glide 加载完成 / 视频播放成功）。
     * 置 [PlaybackUiState.contentReady] 为 true，从而显示三个角的辅助信息；
     * 若当前是图片，则此刻才开始自动切换计时。
     *
     * 幂等：重复调用不会重复启动图片计时。
     */
    fun notifyContentReady() {
        if (_uiState.value.contentReady) return
        _uiState.update { it.copy(contentReady = true) }
        if (_uiState.value.isCurrentImage) {
            scheduleImageAutoNext()
        }
    }

    private fun emitError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false) }
        _events.tryEmit(PlaybackUiEvent.ShowError(message))
    }

}
