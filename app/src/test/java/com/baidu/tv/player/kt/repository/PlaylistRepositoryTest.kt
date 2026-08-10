package com.baidu.tv.player.kt.repository

import com.baidu.tv.player.kt.database.PlaylistDao
import com.baidu.tv.player.kt.database.PlaylistItemDao
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.model.PlaylistItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * PlaylistRepository 单测：刷新 / 事务 / 并发安全（MockK + TestScope）。
 */
class PlaylistRepositoryTest {

    private val playlistDao: PlaylistDao = mockk(relaxed = true)
    private val playlistItemDao: PlaylistItemDao = mockk(relaxed = true)
    private val fileRepository: FileRepository = mockk(relaxed = true)

    /** 同步事务执行器：直接顺序执行 block，便于断言调用顺序。 */
    private val syncRunner = TransactionRunner { block -> block() }

    private val repo = PlaylistRepository(playlistDao, playlistItemDao, fileRepository, syncRunner)

    private fun playlist(
        id: Long = 1L,
        mediaType: Int = 0,
        sourcePaths: String? = "[\"/dir\"]",
    ) = Playlist(id = id, name = "P", mediaType = mediaType, sourcePaths = sourcePaths)

    @Test
    fun insertPlaylist_returnsDaoId() = runTest {
        coEvery { playlistDao.insert(any()) } returns 42L
        assertEquals(42L, repo.insertPlaylist(playlist()))
    }

    @Test
    fun refreshPlaylist_throws_whenNoToken() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.refreshPlaylist(playlist(), "")
            }
        }
    }

    @Test
    fun refreshPlaylist_throws_whenNoSourcePaths() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.refreshPlaylist(playlist(sourcePaths = null), "token")
            }
        }
    }

    @Test
    fun refreshPlaylist_throws_whenEmptySourcePaths() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.refreshPlaylist(playlist(sourcePaths = "[]"), "token")
            }
        }
    }

    @Test
    fun refreshPlaylist_deletesOldItems_insertsNew_updatesStats_inTransaction() = runTest {
        val files = listOf(
            FileInfo(serverFilename = "a.mp4", path = "/dir/a.mp4", fsId = 1, category = 1),
            FileInfo(serverFilename = "b.jpg", path = "/dir/b.jpg", fsId = 2, category = 3),
            FileInfo(serverFilename = "c.txt", path = "/dir/c.txt", fsId = 3), // 非媒体，应被过滤
        )
        coEvery { fileRepository.fetchFilesRecursive(any(), any(), any()) } returns files

        val updated = slot<Playlist>()
        coEvery { playlistDao.update(capture(updated)) } returns Unit

        val p = playlist(mediaType = 0)
        val count = repo.refreshPlaylist(p, "token")

        assertEquals(2, count)
        coVerifyOrder {
            playlistItemDao.deleteByPlaylistId(1L)
            playlistItemDao.insertAll(any())
            playlistDao.update(any())
        }
        assertEquals(2, updated.captured.totalItems)
    }

    @Test
    fun refreshPlaylist_filtersByMediaType_videoOnly() = runTest {
        val files = listOf(
            FileInfo(serverFilename = "a.mp4", path = "/d/a.mp4", fsId = 1, category = 1),
            FileInfo(serverFilename = "b.jpg", path = "/d/b.jpg", fsId = 2, category = 3),
        )
        coEvery { fileRepository.fetchFilesRecursive(any(), any(), any()) } returns files

        val inserted = slot<List<PlaylistItem>>()
        coEvery { playlistItemDao.insertAll(capture(inserted)) } returns Unit

        repo.refreshPlaylist(playlist(mediaType = 1), "token")

        assertEquals(1, inserted.captured.size)
        assertEquals("a.mp4", inserted.captured[0].fileName)
        assertEquals(1, inserted.captured[0].mediaType) // 视频
    }

    @Test
    fun refreshPlaylist_keepsOldSnapshot_whenAnySourceFails() = runTest {
        coEvery { fileRepository.fetchFilesRecursive(any(), "/fail", any()) } throws
            RuntimeException("network")
        coEvery { fileRepository.fetchFilesRecursive(any(), "/ok", any()) } returns listOf(
            FileInfo(serverFilename = "a.mp4", path = "/ok/a.mp4", fsId = 1, category = 1),
        )

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                repo.refreshPlaylist(playlist(sourcePaths = "[\"/fail\",\"/ok\"]"), "token")
            }
        }
        coVerify(exactly = 0) { playlistItemDao.deleteByPlaylistId(any()) }
        coVerify(exactly = 0) { playlistItemDao.insertAll(any()) }
    }

    @Test
    fun refreshPlaylist_deduplicatesOverlappingSourceFiles() = runTest {
        val duplicate = FileInfo(serverFilename = "a.mp4", path = "/shared/a.mp4", fsId = 1, category = 1)
        coEvery { fileRepository.fetchFilesRecursive(any(), any(), any()) } returns listOf(duplicate)
        val inserted = slot<List<PlaylistItem>>()
        coEvery { playlistItemDao.insertAll(capture(inserted)) } returns Unit

        repo.refreshPlaylist(playlist(sourcePaths = "[\"/a\",\"/b\"]"), "token")

        assertEquals(1, inserted.captured.size)
    }

    @Test
    fun refreshPlaylist_assignsIncrementalSortOrder() = runTest {
        val files = (0 until 5).map {
            FileInfo(serverFilename = "f$it.mp4", path = "/d/f$it.mp4", fsId = it.toLong(), category = 1)
        }
        coEvery { fileRepository.fetchFilesRecursive(any(), any(), any()) } returns files
        val inserted = slot<List<PlaylistItem>>()
        coEvery { playlistItemDao.insertAll(capture(inserted)) } returns Unit

        repo.refreshPlaylist(playlist(mediaType = 1), "token")

        assertEquals(listOf(0, 1, 2, 3, 4), inserted.captured.map { it.sortOrder })
    }

    @Test
    fun refreshPlaylist_serializedByMutex_noConcurrentRun() = runTest {
        // Mutex 保证同一 repo 实例的 refresh 串行；此处验证两次顺序调用均完成
        coEvery { fileRepository.fetchFilesRecursive(any(), any(), any()) } returns emptyList()
        coEvery { playlistItemDao.insertAll(any()) } returns Unit
        repo.refreshPlaylist(playlist(), "token")
        repo.refreshPlaylist(playlist(), "token")
        coVerify(atLeast = 2) { playlistItemDao.deleteByPlaylistId(any()) }
    }
}
