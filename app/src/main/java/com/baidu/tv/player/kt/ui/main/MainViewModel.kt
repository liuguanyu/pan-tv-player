package com.baidu.tv.player.kt.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val playbackHistoryRepository: PlaybackHistoryRepository,
    private val authService: BaiduAuthService,
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentTasks: StateFlow<List<PlaybackHistory>> = playbackHistoryRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<MainUiState> = playlists
        .map { MainUiState(isPlaylistEmpty = it.isEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    private val _events = MutableSharedFlow<MainUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<MainUiEvent> = _events.asSharedFlow()

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            runCatching { playlistRepository.deletePlaylist(playlist) }
                .onSuccess { _events.emit(MainUiEvent.PlaylistDeleted(playlist)) }
                .onFailure { _events.emit(MainUiEvent.Error(it.message ?: "删除播放列表失败")) }
        }
    }

    fun refreshPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            _events.emit(MainUiEvent.RefreshStarted(playlist))
            val token = authService.getAccessToken()
                ?: if (authService.refreshToken()) authService.getAccessToken() else null
            runCatching {
                val validToken = token ?: throw IllegalStateException("登录已过期，请重新登录")
                playlistRepository.refreshPlaylist(playlist, validToken)
            }
                .onSuccess { count -> _events.emit(MainUiEvent.RefreshSucceeded(playlist, count)) }
                .onFailure { _events.emit(MainUiEvent.RefreshFailed(playlist, it.message ?: "刷新播放列表失败，请检查网络连接或重新登录")) }
        }
    }
}

data class MainUiState(
    val isPlaylistEmpty: Boolean = true,
)

sealed interface MainUiEvent {
    data class RefreshStarted(val playlist: Playlist) : MainUiEvent
    data class RefreshSucceeded(val playlist: Playlist, val itemCount: Int) : MainUiEvent
    data class RefreshFailed(val playlist: Playlist, val message: String) : MainUiEvent
    data class PlaylistDeleted(val playlist: Playlist) : MainUiEvent
    data class Error(val message: String) : MainUiEvent
}
