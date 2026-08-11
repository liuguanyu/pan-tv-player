package com.baidu.tv.player.kt.ui.filebrowser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.model.PlaylistItem
import com.baidu.tv.player.kt.repository.FileRepository
import com.baidu.tv.player.kt.repository.PlaylistRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val fileRepository: FileRepository,
    private val playlistRepository: PlaylistRepository,
    private val authService: BaiduAuthService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaType: Int = savedStateHandle[FileBrowserActivity.EXTRA_MEDIA_TYPE] ?: MediaType.ALL.value
    private val initialPath: String = savedStateHandle[FileBrowserActivity.EXTRA_INITIAL_PATH] ?: ROOT_PATH
    private val initialMultiSelectMode: Boolean = savedStateHandle[FileBrowserActivity.EXTRA_MULTI_SELECT_MODE] ?: false

    private val pathStack = ArrayDeque<String>()
    private val selectedItemsByPath = linkedMapOf<String, FileInfo>()
    private var hasLoadedInitialPath = false

    private val _uiState = MutableStateFlow(
        FileBrowserUiState(
            currentPath = initialPath,
            multiSelectMode = initialMultiSelectMode,
        ),
    )
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileBrowserUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FileBrowserUiEvent> = _events.asSharedFlow()

    fun loadInitialIfNeeded() {
        if (hasLoadedInitialPath) return
        hasLoadedInitialPath = true
        loadFileList(initialPath)
    }

    fun loadFileList(path: String = uiState.value.currentPath) {
        viewModelScope.launch {
            if (!authService.isAuthenticated()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "未登录，请先登录", files = emptyList(), currentPath = path) }
                return@launch
            }
            val accessToken = authService.getAccessToken().orEmpty()
            if (accessToken.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "未获取到访问令牌，请先登录", files = emptyList(), currentPath = path) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, errorMessage = null, currentPath = path) }
            runCatching {
                if (uiState.value.isRecursive) {
                    fileRepository.getFileListRecursive(accessToken, path, mediaType)
                } else {
                    fileRepository.getFileList(accessToken, path, mediaType)
                }
            }.onSuccess { files ->
                _uiState.update { it.copy(files = sortFiles(files, it.sortMode), currentPath = path, isLoading = false, errorMessage = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(files = emptyList(), currentPath = path, isLoading = false, errorMessage = error.message ?: "加载文件列表失败") }
                _events.emit(FileBrowserUiEvent.Error(error.message ?: "加载文件列表失败"))
            }
        }
    }

    fun enterDirectory(path: String?) {
        if (path.isNullOrBlank()) return
        pathStack.addLast(uiState.value.currentPath)
        loadFileList(path)
    }

    fun onFileClicked(file: FileInfo) {
        if (MediaType.fromCode(mediaType) == MediaType.AUDIO && file.isAudio()) {
            viewModelScope.launch {
                _events.emit(FileBrowserUiEvent.AudioSelected(file.fsId, file.path, file.serverFilename))
            }
            return
        }
        if (uiState.value.multiSelectMode) {
            if (file.isDirectory()) enterDirectory(file.path) else toggleSelection(file)
            return
        }
        if (file.isDirectory()) {
            enterDirectory(file.path)
            return
        }
        val playableFiles = uiState.value.files.filterNot { it.isDirectory() }
        val startIndex = playableFiles.indexOfFirst { it.path == file.path }.coerceAtLeast(0)
        if (playableFiles.isNotEmpty()) {
            viewModelScope.launch {
                _events.emit(
                    FileBrowserUiEvent.OpenPlayback(
                        files = playableFiles,
                        startIndex = startIndex,
                        selectedPath = file.path,
                        folderPath = uiState.value.currentPath,
                        mediaType = mediaType,
                    ),
                )
            }
        }
    }

    fun onFileLongClicked(file: FileInfo): Boolean {
        if (!uiState.value.multiSelectMode) {
            setMultiSelectMode(true)
        }
        toggleSelection(file)
        viewModelScope.launch {
            val selected = uiState.value.selectedPaths.contains(file.path)
            _events.emit(FileBrowserUiEvent.SelectionChanged(if (selected) "已选中: ${file.displayName}" else "已取消: ${file.displayName}"))
        }
        return true
    }

    fun setMultiSelectMode(enabled: Boolean) {
        _uiState.update { it.copy(multiSelectMode = enabled) }
        if (!enabled) clearSelection()
    }

    fun toggleSelection(file: FileInfo) {
        val path = file.path ?: return
        if (selectedItemsByPath.containsKey(path)) selectedItemsByPath.remove(path) else selectedItemsByPath[path] = file
        _uiState.update { it.copy(selectedPaths = selectedItemsByPath.keys.toSet()) }
    }

    fun clearSelection() {
        selectedItemsByPath.clear()
        _uiState.update { it.copy(selectedPaths = emptySet()) }
    }

    fun toggleRecursive() {
        _uiState.update { it.copy(isRecursive = !it.isRecursive) }
        loadFileList(uiState.value.currentPath)
    }

    fun toggleSortMode() {
        _uiState.update { state ->
            val next = state.sortMode.next()
            state.copy(sortMode = next, files = sortFiles(state.files, next))
        }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isGridMode = !it.isGridMode) }
    }

    fun playCurrentList() {
        val playableFiles = uiState.value.files.filterNot { it.isDirectory() }
        if (playableFiles.isEmpty()) {
            viewModelScope.launch { _events.emit(FileBrowserUiEvent.Error("当前列表没有可播放文件")) }
            return
        }
        viewModelScope.launch { _events.emit(FileBrowserUiEvent.OpenPlayback(playableFiles, 0, uiState.value.currentPath, mediaType)) }
    }

    fun confirmSelection() {
        val selectedItems = selectedItemsByPath.values.toList()
        if (selectedItems.isEmpty()) {
            viewModelScope.launch { _events.emit(FileBrowserUiEvent.Error("请至少选择一个目录或文件")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingPlaylist = true) }
            _events.emit(FileBrowserUiEvent.PlaylistCreationStarted)

            val accessToken = authService.getAccessToken().orEmpty()
            if (accessToken.isEmpty()) {
                _uiState.update { it.copy(isCreatingPlaylist = false) }
                _events.emit(FileBrowserUiEvent.PlaylistCreationFailed("未获取到访问令牌，请先登录"))
                return@launch
            }

            runCatching { createPlaylistFromSelection(accessToken, selectedItems) }
                .onSuccess { result ->
                    clearSelection()
                    _uiState.update { it.copy(isCreatingPlaylist = false) }
                    _events.emit(FileBrowserUiEvent.PlaylistCreationSucceeded(result.playlistId, result.itemCount))
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isCreatingPlaylist = false) }
                    _events.emit(FileBrowserUiEvent.PlaylistCreationFailed(error.message ?: "创建播放列表失败"))
                }
        }
    }

    fun goBack(): Boolean {
        val previous = pathStack.removeLastOrNull() ?: return false
        loadFileList(previous)
        return true
    }

    fun canGoBack(): Boolean = pathStack.isNotEmpty()

    private suspend fun createPlaylistFromSelection(accessToken: String, selectedItems: List<FileInfo>): PlaylistCreationResult {
        val recursive = uiState.value.isRecursive
        val scanResults = supervisorScope {
            selectedItems.map { item ->
                async {
                    when {
                        item.isDirectory() -> {
                            val path = item.path ?: return@async ScanResult(emptyList(), null, "目录路径为空")
                            val files = if (recursive) {
                                fileRepository.fetchFilesRecursive(accessToken, path)
                            } else {
                                fileRepository.fetchFilesNonRecursive(accessToken, path)
                            }
                            ScanResult(files.filterMediaFiles(), path, null)
                        }
                        item.isSupportedMedia() -> ScanResult(listOf(item), item.parentPath(), null)
                        else -> ScanResult(emptyList(), null, null)
                    }
                }
            }.awaitAll()
        }

        val failedCount = scanResults.count { it.errorMessage != null }
        val mediaFiles = scanResults.flatMap { it.files }.distinctBy { it.fsId.takeIf { id -> id != 0L }?.toString() ?: it.path.orEmpty() }
        if (mediaFiles.isEmpty()) {
            val suffix = if (failedCount > 0) "，其中 $failedCount 个目录扫描失败" else ""
            throw IllegalStateException("未找到任何媒体文件$suffix")
        }

        val sourcePaths = scanResults.mapNotNull { it.sourcePath }.distinct()
        val playlistName = sourcePaths.firstOrNull()?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "新建播放列表"
        val coverPath = mediaFiles.firstOrNull { it.isImage() && it.thumbs?.url1 != null }?.thumbs?.url1
            ?: mediaFiles.firstOrNull { it.isVideo() && it.thumbs?.url1 != null }?.thumbs?.url1
            ?: ""
        val playlistMediaType = when (MediaType.fromCode(mediaType)) {
            MediaType.VIDEO -> 1
            MediaType.IMAGE -> 2
            MediaType.ALL -> 0
            MediaType.AUDIO -> 0
        }
        val playlist = Playlist(
            name = playlistName,
            createdAt = System.currentTimeMillis(),
            mediaType = playlistMediaType,
            coverImagePath = coverPath,
            totalItems = mediaFiles.size,
            sourcePaths = Gson().toJson(sourcePaths),
        )
        val items = mediaFiles.mapIndexed { index, file ->
            PlaylistItem(
                fsId = file.fsId,
                filePath = file.path,
                fileName = file.serverFilename,
                mediaType = if (file.isVideo()) 1 else 2,
                sortOrder = index,
                duration = 0,
                fileSize = file.size,
            )
        }
        val playlistId = playlistRepository.createPlaylistWithItems(playlist, items)
        return PlaylistCreationResult(playlistId, items.size)
    }

    private fun List<FileInfo>.filterMediaFiles(): List<FileInfo> = filter { it.isSupportedMedia() }

    private fun FileInfo.isSupportedMedia(): Boolean = !isDirectory() && when (MediaType.fromCode(mediaType)) {
        MediaType.IMAGE -> isImage()
        MediaType.VIDEO -> isVideo()
        MediaType.ALL -> isImage() || isVideo()
        MediaType.AUDIO -> isAudio()
    }

    private fun FileInfo.parentPath(): String? = path?.substringBeforeLast('/', missingDelimiterValue = "")?.takeIf { it.isNotBlank() } ?: ROOT_PATH

    private fun sortFiles(files: List<FileInfo>, mode: SortMode): List<FileInfo> {
        val comparator = compareBy<FileInfo> { !it.isDirectory() }.then(
            when (mode) {
                SortMode.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.serverFilename.orEmpty() }
                SortMode.NAME_DESC -> compareByDescending<FileInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.serverFilename.orEmpty() }
                SortMode.DATE_ASC -> compareBy { it.serverMtime }
                SortMode.DATE_DESC -> compareByDescending { it.serverMtime }
            },
        )
        return files.sortedWith(comparator)
    }

    companion object {
        private const val ROOT_PATH = "/"
    }
}

data class FileBrowserUiState(
    val files: List<FileInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isCreatingPlaylist: Boolean = false,
    val errorMessage: String? = null,
    val currentPath: String = "/",
    val sortMode: SortMode = SortMode.NAME_ASC,
    val isRecursive: Boolean = false,
    val multiSelectMode: Boolean = false,
    val isGridMode: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
)

enum class SortMode(val label: String) {
    NAME_ASC("排序: 文件名↑"),
    NAME_DESC("排序: 文件名↓"),
    DATE_ASC("排序: 日期↑"),
    DATE_DESC("排序: 日期↓");

    fun next(): SortMode = when (this) {
        NAME_ASC -> NAME_DESC
        NAME_DESC -> DATE_ASC
        DATE_ASC -> DATE_DESC
        DATE_DESC -> NAME_ASC
    }
}

sealed interface FileBrowserUiEvent {
    data class AudioSelected(val fsId: Long, val path: String?, val name: String?) : FileBrowserUiEvent
    data class OpenPlayback(
        val files: List<FileInfo>,
        val startIndex: Int,
        val folderPath: String,
        val mediaType: Int,
        val selectedPath: String? = null,
    ) : FileBrowserUiEvent
    data object PlaylistCreationStarted : FileBrowserUiEvent
    data class PlaylistCreationSucceeded(val playlistId: Long, val itemCount: Int) : FileBrowserUiEvent
    data class PlaylistCreationFailed(val message: String) : FileBrowserUiEvent
    data class SelectionChanged(val message: String) : FileBrowserUiEvent
    data class Error(val message: String) : FileBrowserUiEvent
}

private data class ScanResult(
    val files: List<FileInfo>,
    val sourcePath: String?,
    val errorMessage: String?,
)

private data class PlaylistCreationResult(
    val playlistId: Long,
    val itemCount: Int,
)

private val FileInfo.displayName: String
    get() = serverFilename ?: path ?: "未知文件"
