package com.baidu.tv.player.kt.ui.filebrowser

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.model.PlaylistItem
import com.baidu.tv.player.kt.repository.FileRepository
import com.baidu.tv.player.kt.repository.PlaylistRepository
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FileBrowserViewModelTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var fileRepository: FileRepository
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var authService: BaiduAuthService

    @Before
    fun setUp() {
        fileRepository = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        authService = mockk(relaxed = true)
        every { authService.isAuthenticated() } returns true
        every { authService.getAccessToken() } returns "token"
    }

    @Test
    fun loadInitial_loadsDirectoryAndSortsDirectoriesFirst() = runTest {
        val files = listOf(
            file("b.mp4", path = "/root/b.mp4", category = 1, mtime = 2),
            file("dir", path = "/root/dir", isdir = 1, mtime = 3),
            file("a.jpg", path = "/root/a.jpg", category = 3, mtime = 1),
        )
        coEvery { fileRepository.getFileList("token", "/root", MediaType.ALL.value) } returns files
        val vm = viewModel(initialPath = "/root")

        assertEquals("/root", vm.uiState.value.currentPath)
        vm.loadInitialIfNeeded()
        advanceUntilIdle()

        val loaded = vm.uiState.value
        assertFalse(loaded.isLoading)
        assertEquals(listOf("dir", "a.jpg", "b.mp4"), loaded.files.map { it.serverFilename })
        coVerify { fileRepository.getFileList("token", "/root", MediaType.ALL.value) }
    }

    @Test
    fun toggleRecursive_usesRecursiveRepositoryAndUpdatesState() = runTest {
        coEvery { fileRepository.getFileListRecursive("token", "/root", MediaType.ALL.value) } returns listOf(file("deep.mp4", category = 1))
        val vm = viewModel(initialPath = "/root")

        assertFalse(vm.uiState.value.isRecursive)
        vm.toggleRecursive()
        advanceUntilIdle()

        val loaded = vm.uiState.value
        assertTrue(loaded.isRecursive)
        assertEquals(listOf("deep.mp4"), loaded.files.map { it.serverFilename })
        coVerify { fileRepository.getFileListRecursive("token", "/root", MediaType.ALL.value) }
    }

    @Test
    fun toggleSortMode_cyclesAndResortsByDate() = runTest {
        coEvery { fileRepository.getFileList("token", "/", MediaType.ALL.value) } returns listOf(
            file("b.mp4", category = 1, mtime = 20),
            file("a.mp4", category = 1, mtime = 10),
        )
        val vm = viewModel()
        vm.loadInitialIfNeeded()

        vm.uiState.test {
            skipItems(1)
            vm.toggleSortMode()
            assertEquals(SortMode.NAME_DESC, awaitItem().sortMode)
            vm.toggleSortMode()
            val dateAsc = awaitItem()
            assertEquals(SortMode.DATE_ASC, dateAsc.sortMode)
            assertEquals(listOf("a.mp4", "b.mp4"), dateAsc.files.map { it.serverFilename })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun multiSelect_clickFileTogglesSelection_longClickDirectorySelects() = runTest {
        val vm = viewModel(multiSelectMode = true)
        val media = file("a.mp4", path = "/a.mp4", category = 1)
        val dir = file("dir", path = "/dir", isdir = 1)

        vm.uiState.test {
            assertTrue(awaitItem().multiSelectMode)
            vm.onFileClicked(media)
            assertEquals(setOf("/a.mp4"), awaitItem().selectedPaths)
            vm.onFileLongClicked(dir)
            assertEquals(setOf("/a.mp4", "/dir"), awaitItem().selectedPaths)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmSelection_createsPlaylistFromDirectory_successEvent() = runTest {
        val dir = file("trip", path = "/trip", isdir = 1)
        coEvery { fileRepository.fetchFilesRecursive("token", "/trip", any()) } returns listOf(
            file("a.mp4", path = "/trip/a.mp4", category = 1, fsId = 1),
            file("b.jpg", path = "/trip/b.jpg", category = 3, fsId = 2),
            file("c.txt", path = "/trip/c.txt", category = 0, fsId = 3),
        )
        coEvery { playlistRepository.createPlaylistWithItems(any(), any()) } returns 42L
        val vm = viewModel(multiSelectMode = true)
        vm.toggleRecursive()
        advanceUntilIdle()

        vm.events.test {
            vm.onFileLongClicked(dir)
            assertTrue(awaitItem() is FileBrowserUiEvent.SelectionChanged)
            vm.confirmSelection()
            assertEquals(FileBrowserUiEvent.PlaylistCreationStarted, awaitItem())
            assertEquals(FileBrowserUiEvent.PlaylistCreationSucceeded(42L, 2), awaitItem())
            coVerify { playlistRepository.createPlaylistWithItems(any(), match { it.size == 2 }) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmSelection_failureEmitsFailedEvent() = runTest {
        val media = file("a.mp4", path = "/a.mp4", category = 1)
        coEvery { playlistRepository.createPlaylistWithItems(any(), any()) } throws IllegalStateException("db failed")
        val vm = viewModel(multiSelectMode = true)
        vm.onFileClicked(media)

        vm.events.test {
            vm.confirmSelection()
            assertEquals(FileBrowserUiEvent.PlaylistCreationStarted, awaitItem())
            assertEquals(FileBrowserUiEvent.PlaylistCreationFailed("db failed"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun confirmSelection_filtersByImageMediaTypeAndPersistsPlaylistShape() = runTest {
        val image = file("a.jpg", path = "/a.jpg", category = 3, fsId = 10)
        val video = file("v.mp4", path = "/v.mp4", category = 1, fsId = 11)
        val playlistSlot = slot<Playlist>()
        val itemsSlot = slot<List<PlaylistItem>>()
        coEvery { playlistRepository.createPlaylistWithItems(capture(playlistSlot), capture(itemsSlot)) } returns 9L
        val vm = viewModel(mediaType = MediaType.IMAGE.value, multiSelectMode = true)
        vm.onFileClicked(image)
        vm.onFileClicked(video)

        vm.confirmSelection()

        coVerify { playlistRepository.createPlaylistWithItems(any(), any()) }
        assertEquals(2, playlistSlot.captured.mediaType)
        assertEquals(1, itemsSlot.captured.size)
        assertEquals("a.jpg", itemsSlot.captured[0].fileName)
        assertEquals(2, itemsSlot.captured[0].mediaType)
    }

    private fun viewModel(
        mediaType: Int = MediaType.ALL.value,
        initialPath: String = "/",
        multiSelectMode: Boolean = false,
    ): FileBrowserViewModel = FileBrowserViewModel(
        fileRepository,
        playlistRepository,
        authService,
        SavedStateHandle(
            mapOf(
                FileBrowserActivity.EXTRA_MEDIA_TYPE to mediaType,
                FileBrowserActivity.EXTRA_INITIAL_PATH to initialPath,
                FileBrowserActivity.EXTRA_MULTI_SELECT_MODE to multiSelectMode,
            ),
        ),
    )

    private fun file(
        name: String,
        path: String = "/$name",
        isdir: Int = 0,
        category: Int = 0,
        mtime: Long = 0,
        fsId: Long = name.hashCode().toLong(),
    ) = FileInfo(fsId = fsId, path = path, serverFilename = name, isdir = isdir, category = category, serverMtime = mtime)
}
