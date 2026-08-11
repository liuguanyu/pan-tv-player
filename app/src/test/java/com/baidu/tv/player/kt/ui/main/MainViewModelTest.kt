package com.baidu.tv.player.kt.ui.main

import app.cash.turbine.test
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.repository.PlaylistRepository
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var playbackHistoryRepository: PlaybackHistoryRepository
    private lateinit var authService: BaiduAuthService
    private lateinit var playlistsFlow: MutableStateFlow<List<Playlist>>
    private lateinit var recentTasksFlow: MutableStateFlow<List<PlaybackHistory>>
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        playlistRepository = mockk(relaxed = true)
        playbackHistoryRepository = mockk(relaxed = true)
        authService = mockk(relaxed = true)
        playlistsFlow = MutableStateFlow(emptyList())
        recentTasksFlow = MutableStateFlow(emptyList())

        every { playlistRepository.getAllPlaylists() } returns playlistsFlow
        every { playbackHistoryRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns recentTasksFlow
        every { authService.getAccessToken() } returns "access-token"

        viewModel = MainViewModel(playlistRepository, playbackHistoryRepository, authService)
    }

    @Test
    fun playlists_emitsLoadedPlaylists() = runTest {
        val playlist = Playlist(id = 1L, name = "旅行", totalItems = 3)

        viewModel.playlists.test {
            assertEquals(emptyList<Playlist>(), awaitItem())
            playlistsFlow.value = listOf(playlist)
            assertEquals(listOf(playlist), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_reflectsEmptyPlaylistState() = runTest {
        val playlist = Playlist(id = 2L, name = "家庭", totalItems = 5)

        viewModel.uiState.test {
            assertTrue(awaitItem().isPlaylistEmpty)
            playlistsFlow.value = listOf(playlist)
            assertFalse(awaitItem().isPlaylistEmpty)
            playlistsFlow.value = emptyList()
            assertTrue(awaitItem().isPlaylistEmpty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun recentTasks_emitsTop4History() = runTest {
        val history = PlaybackHistory(id = 1L, folderPath = "/照片", folderName = "照片", mediaType = 1, fileCount = 8)

        viewModel.recentTasks.test {
            assertEquals(emptyList<PlaybackHistory>(), awaitItem())
            recentTasksFlow.value = listOf(history)
            assertEquals(listOf(history), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshPlaylist_emitsStartedAndSucceededEvents() = runTest {
        val playlist = Playlist(id = 3L, name = "刷新", sourcePaths = "[/刷新]")
        coEvery { playlistRepository.refreshPlaylist(playlist, "access-token") } returns 7

        viewModel.events.test {
            viewModel.refreshPlaylist(playlist)
            assertEquals(MainUiEvent.RefreshStarted(playlist), awaitItem())
            assertEquals(MainUiEvent.RefreshSucceeded(playlist, 7), awaitItem())
            coVerify { playlistRepository.refreshPlaylist(playlist, "access-token") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun renamePlaylist_updatesNameAndEmitsEvent() = runTest {
        val playlist = Playlist(id = 4L, name = "旧名称")
        val renamed = playlist.copy(name = "新名称")
        coEvery { playlistRepository.updatePlaylist(renamed) } returns Unit

        viewModel.events.test {
            viewModel.renamePlaylist(playlist, "  新名称  ")
            assertEquals(MainUiEvent.PlaylistRenamed(renamed), awaitItem())
            coVerify { playlistRepository.updatePlaylist(renamed) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deletePlaylist_emitsDeletedEvent() = runTest {
        val playlist = Playlist(id = 4L, name = "删除")
        coEvery { playlistRepository.deletePlaylist(playlist) } returns Unit

        viewModel.events.test {
            viewModel.deletePlaylist(playlist)
            assertEquals(MainUiEvent.PlaylistDeleted(playlist), awaitItem())
            coVerify { playlistRepository.deletePlaylist(playlist) }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
