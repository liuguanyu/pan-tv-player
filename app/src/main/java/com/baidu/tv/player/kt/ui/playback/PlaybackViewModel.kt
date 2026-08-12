package com.baidu.tv.player.kt.ui.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.location.LocationExtractionService
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.ImageEffect
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.repository.FileRepository
import com.baidu.tv.player.kt.repository.PlayableUrlResolver
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.random.Random

private const val DEFAULT_IMAGE_DISPLAY_MS = 8_000L
private const val DEFAULT_TRANSITION_MS = 1_000L

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
 * 播放页 ViewModel（对应 tasks 6.7）。
 *
 * - 通过 [PlaylistCache] 接收文件浏览页传入的临时播放列表。
 * - [prepareAndEmitCurrent] 获取/复用 dlink，并拼接 access_token 生成播放 URL。
 * - [preloadNextFile] 使用 [preloadMutex] 保证下一首 dlink 预加载不会并发重复请求。
 * - 播放历史通过 [PlaybackHistoryRepository.insert] upsert 落库。
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val authService: BaiduAuthService,
    private val fileRepository: FileRepository,
    private val historyRepository: PlaybackHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val locationExtractionService: LocationExtractionService,
    private val urlResolver: PlayableUrlResolver,
    private val sessionFactory: PlaybackSessionFactory,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlaybackUiEvent> = _events.asSharedFlow()

    private val dlinkCache = LinkedHashMap<Long, String>()
    /** 获取播放链接时返回的完整文件详情，复用于最近播放封面，避免丢失 thumbs。 */
    private val fileDetailCache = LinkedHashMap<Long, FileInfo>()
    private val preloadMutex = Mutex()
    private var preloadJob: Job? = null
    private var locationJob: Job? = null
    private var imageAutoNextJob: Job? = null
    private var randomQueue = ArrayDeque<Int>()

    init {
        observeSettings()
    }

    /** 订阅设置仓库，让设置页的修改（播放模式/特效/背景/时长）实时作用于播放页。 */
    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.playMode.collect { mode ->
                randomQueue.clear()
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
        viewModelScope.launch {
            prepareAndEmitCurrent()
        }
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
        val nextIndex = calculateNextIndex(forward = true) ?: return
        switchToIndex(nextIndex)
    }

    fun playPrevious() {
        val prevIndex = calculateNextIndex(forward = false) ?: return
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
        randomQueue.clear()
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
        viewModelScope.launch { prepareAndEmitCurrent() }
    }

    fun preloadNextFile() {
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            preloadMutex.withLock {
                val state = _uiState.value
                val nextIndex = calculateNextIndexForState(state, forward = true) ?: return@withLock
                val file = state.files.getOrNull(nextIndex) ?: return@withLock
                if (dlinkCache.containsKey(file.fsId)) return@withLock
                runCatching { resolvePlayableUrl(file) }
                    .onSuccess { dlinkCache[file.fsId] = it }
            }
        }
    }

    internal suspend fun awaitPreloadForTest() {
        preloadJob?.join()
    }

    private fun switchToIndex(index: Int) {
        val files = _uiState.value.files
        if (index !in files.indices) return
        _uiState.update {
            it.copy(
                currentIndex = index,
                currentFile = files[index],
                preparedUrl = null,
                positionMs = 0L,
                durationMs = 0L,
                isLoading = true,
                errorMessage = null,
                // 切换到新媒体：首帧尚未渲染，先隐藏三个角信息并停止计时，待就绪回调再置 true。
                contentReady = false,
            )
        }
        viewModelScope.launch { prepareAndEmitCurrent() }
    }

    private suspend fun prepareAndEmitCurrent() {
        val file = _uiState.value.currentFile ?: return
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        runCatching { resolvePlayableUrl(file) }
            .onSuccess { url ->
                dlinkCache[file.fsId] = url
                _uiState.update { it.copy(preparedUrl = url, isLoading = false, isPlaying = true) }
                // 文件真正开始播放：记录该文件到"最近播放"（文件级，去重 + FIFO 由仓库处理）。
                insertCurrentFileHistory(file)
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
            .onFailure { throwable ->
                val msg = throwable.message ?: "获取播放链接失败"
                _uiState.update { it.copy(isLoading = false, errorMessage = msg, isPlaying = false) }
                _events.emit(PlaybackUiEvent.ShowError(msg))
            }
    }

    /**
     * 异步提取当前媒体拍摄地点（EXIF GPS → 逆地理编码），结果写入 uiState.locationText。
     * 切换文件时取消上一个提取任务并先清空旧地点；失败静默（locationText 保持 null）。
     */
    private fun extractLocationFor(url: String, isVideo: Boolean) {
        locationJob?.cancel()
        // 切换文件先清空旧的地点与拍摄时间，避免残留上一媒体信息。
        _uiState.update { it.copy(locationText = null, captureTimeText = null) }
        locationJob = viewModelScope.launch {
            // 地点：受"显示地点"设置控制。
            launch {
                if (!_uiState.value.showLocation) return@launch
                val location = runCatching {
                    locationExtractionService.extractLocation(url, isVideo)
                }.getOrNull()
                if (location != null) {
                    _uiState.update { it.copy(locationText = location) }
                }
            }
            // 拍摄时间：独立于地点，能取到即显示（与地点并行提取，互不阻塞）。
            launch {
                val time = runCatching {
                    locationExtractionService.extractCaptureTime(url, isVideo)
                }.getOrNull()
                if (!time.isNullOrBlank()) {
                    _uiState.update { it.copy(captureTimeText = time) }
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

    /**
     * 由 UI 层在媒体首帧真正渲染完成后调用（图片 Glide 加载完成 / 视频播放成功）。
     * 置 [PlaybackUiState.contentReady] 为 true，从而显示三个角的辅助信息；
     * 若当前是图片，则此刻才开始自动切换计时。
     *
     * 幂等：重复调用不会重复启动图片计时。
     */
    /** 播放页成功渲染后，将本地截图写回当前历史记录。 */
    fun updateHistoryCover(filePath: String, coverPath: String) {
        if (filePath.isBlank()) return
        viewModelScope.launch {
            historyRepository.updateCover(filePath, coverPath)
        }
    }

    fun notifyContentReady() {
        if (_uiState.value.contentReady) return
        _uiState.update { it.copy(contentReady = true) }
        if (_uiState.value.isCurrentImage) {
            scheduleImageAutoNext()
        }
    }

    private suspend fun resolvePlayableUrl(file: FileInfo): String {
        dlinkCache[file.fsId]?.let { return it }
        // 保留 fileDetailCache：文件无 dlink 时先查缓存或拉取详情，为封面缩略图留存 thumbs。
        val dlink = file.dlink?.takeIf { it.isNotBlank() }
            ?: fileDetailCache[file.fsId]?.dlink?.takeIf { it.isNotBlank() }
            ?: fetchAndCacheFileDetail(file.fsId)?.dlink?.takeIf { it.isNotBlank() }
        val url = urlResolver.resolve(file.fsId, dlink, file.serverFilename)
        dlinkCache[file.fsId] = url
        return url
    }

    private suspend fun fetchAndCacheFileDetail(fsId: Long): FileInfo? {
        val token = authService.getAccessToken().orEmpty()
        check(token.isNotEmpty()) { "未获取到访问令牌，请先登录" }
        return fileRepository.fetchFileDetail(token, fsId)?.also { fileDetailCache[fsId] = it }
    }

    private fun calculateNextIndex(forward: Boolean): Int? = calculateNextIndexForState(_uiState.value, forward)

    private fun calculateNextIndexForState(state: PlaybackUiState, forward: Boolean): Int? {
        val size = state.files.size
        if (size == 0) return null
        if (size == 1) return 0
        return when (state.playMode) {
            PlayMode.SINGLE -> state.currentIndex
            PlayMode.SEQUENTIAL -> if (forward) (state.currentIndex + 1) % size else (state.currentIndex - 1 + size) % size
            PlayMode.REVERSE -> if (forward) (state.currentIndex - 1 + size) % size else (state.currentIndex + 1) % size
            PlayMode.RANDOM -> nextRandomIndex(size, state.currentIndex)
        }
    }

    private fun nextRandomIndex(size: Int, currentIndex: Int): Int {
        if (randomQueue.isEmpty()) {
            val candidates = (0 until size).filter { it != currentIndex }.shuffled(Random(System.nanoTime()))
            randomQueue.addAll(candidates)
        }
        return randomQueue.removeFirstOrNull() ?: ((currentIndex + 1) % size)
    }



    /**
     * 记录**当前正在播放的单个文件**到最近播放（文件级）。
     *
     * 在文件真正开始播放（[prepareAndEmitCurrent] 成功）后调用，携带来源上下文，
     * 使点击历史时能重建列表并从该文件开始。去重与 FIFO 裁剪在仓库层处理。
     */
    private suspend fun insertCurrentFileHistory(file: FileInfo) {
        val state = _uiState.value
        val filePath = file.path?.takeIf { it.isNotBlank() }
            ?: file.serverFilename?.let { name ->
                val folder = state.folderPath.trimEnd('/')
                if (folder.isNotEmpty()) "$folder/$name" else "/$name"
            }
            ?: return
        val fileName = file.serverFilename ?: filePath.substringAfterLast('/')
        val mediaType = if (file.isVideo()) MediaType.VIDEO.code else MediaType.IMAGE.code
        // 数据库播放列表项不保存 thumbs，但获取 dlink 的 filemetas 接口会返回完整文件详情。
        // 优先使用原始列表缩略图，再使用详情缩略图，并按清晰度 URL 逐级兜底。
        // 注意：视频文件的 thumbs.urlX 可能是文件下载链接（含 /file/），需跳过。
        val thumbs = file.thumbs ?: fileDetailCache[file.fsId]?.thumbs
        val cover = sequenceOf(thumbs?.icon, thumbs?.url1, thumbs?.url2, thumbs?.url3)
            .firstOrNull { url -> !url.isNullOrBlank() && !url.contains("/file/") }

        try {
            historyRepository.insert(
                PlaybackHistory(
                    folderPath = filePath,
                    folderName = fileName,
                    mediaType = mediaType,
                    fileCount = 1,
                    createTime = System.currentTimeMillis(),
                    coverImagePath = cover,
                    sourcePlaylistId = state.sourcePlaylistId,
                    sourceFolderPath = state.sourceFolderPath,
                    fsId = file.fsId,
                ),
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            // 最近播放属于附属数据：写库失败时保留内存中的即时排序，不中断已成功的媒体播放。
            Log.e(TAG, "更新最近播放失败: $filePath", throwable)
        }
    }

    private fun emitError(message: String) {
        _uiState.update { it.copy(errorMessage = message, isLoading = false) }
        _events.tryEmit(PlaybackUiEvent.ShowError(message))
    }

    private companion object {
        const val TAG = "PlaybackViewModel"
    }
}
