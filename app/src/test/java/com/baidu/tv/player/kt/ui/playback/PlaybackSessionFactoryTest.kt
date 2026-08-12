package com.baidu.tv.player.kt.ui.playback

import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlayMode
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.model.PlaylistItem
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.repository.PlaylistRepository
import com.baidu.tv.player.kt.repository.SettingsRepository
import com.baidu.tv.player.kt.ui.playback.image.ImageBackgroundMode
import com.baidu.tv.player.kt.util.PlaylistCache
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PlaybackSessionFactory] 单测（Task 7.2-7.3）。
 *
 * 覆盖三种来源：目录缓存、数据库播放列表、最近播放历史。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSessionFactoryTest {

    private lateinit var playlistCache: PlaylistCache
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var historyRepository: PlaybackHistoryRepository
    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        playlistCache = PlaylistCache()
        playlistRepository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        every { settingsRepository.playMode } returns MutableStateFlow(PlayMode.SEQUENTIAL)
        every { settingsRepository.imageEffect } returns MutableStateFlow(com.baidu.tv.player.kt.model.ImageEffect.FADE)
        every { settingsRepository.backgroundMode } returns MutableStateFlow(ImageBackgroundMode.DOMINANT_COLOR)
        every { settingsRepository.imageDisplayDurationMs } returns MutableStateFlow(10_000)
        every { settingsRepository.imageTransitionDurationMs } returns MutableStateFlow(1_000)
        every { settingsRepository.showLocation } returns MutableStateFlow(true)
        every { settingsRepository.showCounter } returns MutableStateFlow(true)
        every { settingsRepository.showCaptureTime } returns MutableStateFlow(true)
    }

    private fun factory() = PlaybackSessionFactory(
        playlistCache = playlistCache,
        playlistRepository = playlistRepository,
        historyRepository = historyRepository,
        settingsRepository = settingsRepository,
        queueNavigator = PlaybackQueueNavigator(),
    )

    private fun video(name: String, fsId: Long) = FileInfo(
        fsId = fsId,
        path = "/movies/$name",
        serverFilename = name,
        category = 1,
    )

    private fun image(name: String, fsId: Long) = FileInfo(
        fsId = fsId,
        path = "/photos/$name",
        serverFilename = name,
        category = 3,
    )

    private fun history(id: Long, name: String, fsId: Long) = PlaybackHistory(
        id = id,
        folderPath = "/movies/$name",
        folderName = name,
        mediaType = MediaType.VIDEO.code,
        fileCount = 1,
        lastPlayTime = id,
        createTime = id,
        fsId = fsId,
    )

    // ==================================================================
    // 7.2: 目录缓存
    // ==================================================================

    @Test
    fun fromDirectoryCache_validPlaylistId_returnsSessionWithCorrectItemsAndStartIndex() {
        val files = listOf(video("a.mp4", 1), video("b.mp4", 2), video("c.mp4", 3))
        playlistCache.put("p", files)
        val session = factory().fromDirectoryCache("p", MediaType.VIDEO.code, "/movies", 1, null)

        assertEquals(PlaybackSource.DIRECTORY_CACHE, session.source)
        assertEquals("p", session.playlistId)
        assertEquals(3, session.items.size)
        assertEquals(listOf(1L, 2L, 3L), session.items.map { it.fsId })
        assertEquals(1, session.startIndex)
        assertEquals(MediaType.VIDEO.code, session.mediaType)
        assertEquals("/movies", session.folderPath)
        assertEquals("movies", session.title)
        assertEquals(null, session.sourcePlaylistId)
        assertEquals("/movies", session.sourceFolderPath)
    }

    @Test
    fun fromDirectoryCache_selectedPathOverridesStartIndex() {
        val files = listOf(video("a.mp4", 1), video("b.mp4", 2), video("c.mp4", 3))
        playlistCache.put("p", files)
        val session = factory().fromDirectoryCache("p", MediaType.VIDEO.code, "/movies", 0, "/movies/b.mp4")

        assertEquals(1, session.startIndex)
        assertEquals(2L, session.items[session.startIndex].fsId)
    }

    @Test
    fun fromDirectoryCache_cacheMiss_throwsEmptyError() {
        val ex = assertThrows(PlaybackSessionException::class.java) {
            factory().fromDirectoryCache("missing", MediaType.VIDEO.code, "/movies", 0, null)
        }
        assertEquals("播放列表为空", ex.message)
    }

    @Test
    fun fromDirectoryCache_emptyList_throwsEmptyError() {
        playlistCache.put("p", emptyList())
        val ex = assertThrows(PlaybackSessionException::class.java) {
            factory().fromDirectoryCache("p", MediaType.VIDEO.code, "/movies", 0, null)
        }
        assertEquals("播放列表为空", ex.message)
    }

    @Test
    fun fromDirectoryCache_consumesCacheEntry() {
        val files = listOf(video("a.mp4", 1))
        playlistCache.put("p", files)
        val f = factory()
        f.fromDirectoryCache("p", MediaType.VIDEO.code, "/movies", 0, null)
        // 第二次调用应抛异常，因为缓存已被消费
        val ex = assertThrows(PlaybackSessionException::class.java) {
            f.fromDirectoryCache("p", MediaType.VIDEO.code, "/movies", 0, null)
        }
        assertEquals("播放列表为空", ex.message)
    }

    @Test
    fun fromDirectoryCache_reverseModeStartsFromLastIndex() {
        every { settingsRepository.playMode } returns MutableStateFlow(PlayMode.REVERSE)
        val files = listOf(video("a.mp4", 1), video("b.mp4", 2), video("c.mp4", 3))
        playlistCache.put("p", files)
        val session = factory().fromDirectoryCache("p", MediaType.VIDEO.code, "/movies", 0, null)

        assertEquals(2, session.startIndex)
    }

    @Test
    fun fromDirectoryCache_chineseFolderPath_derivesFolderName() {
        val files = listOf(video("a.mp4", 1))
        playlistCache.put("p", files)
        val session = factory().fromDirectoryCache("p", MediaType.VIDEO.code, "/网盘/中文目录", 0, null)

        assertEquals("中文目录", session.title)
    }

    // ==================================================================
    // 7.2: 数据库播放列表
    // ==================================================================

    @Test
    fun fromDatabasePlaylist_validId_returnsSessionWithItemsMappedFromPlaylistItem() = runTest {
        val playlist = Playlist(id = 1, name = "我的列表", mediaType = 1, lastPlayedIndex = 0)
        val items = listOf(
            PlaylistItem(id = 10, playlistId = 1, fsId = 100, filePath = "/a.mp4", fileName = "a.mp4", mediaType = 1, fileSize = 5000),
            PlaylistItem(id = 11, playlistId = 1, fsId = 200, filePath = "/b.mp4", fileName = "b.mp4", mediaType = 1, fileSize = 6000),
        )
        coEvery { playlistRepository.getPlaylistByIdSync(1) } returns playlist
        coEvery { playlistRepository.getPlaylistItemsSync(1) } returns items

        val session = factory().fromDatabasePlaylist(1, null)

        assertEquals(PlaybackSource.DATABASE_PLAYLIST, session.source)
        assertEquals("1", session.playlistId)
        assertEquals(2, session.items.size)
        assertEquals(listOf(100L, 200L), session.items.map { it.fsId })
        assertEquals("a.mp4", session.items[0].serverFilename)
        assertEquals(5000L, session.items[0].size)
        assertEquals(1, session.items[0].category) // video
        assertEquals(MediaType.VIDEO.code, session.mediaType)
        assertEquals("我的列表", session.folderPath)
        assertEquals("我的列表", session.title)
        assertEquals(1L, session.sourcePlaylistId)
        assertEquals(null, session.sourceFolderPath)
    }

    @Test
    fun fromDatabasePlaylist_startFsIdLocatesCorrectItem() = runTest {
        val playlist = Playlist(id = 1, name = "列表", mediaType = 0, lastPlayedIndex = 0)
        val items = listOf(
            PlaylistItem(id = 10, playlistId = 1, fsId = 100, fileName = "a.mp4", mediaType = 1),
            PlaylistItem(id = 11, playlistId = 1, fsId = 200, fileName = "b.jpg", mediaType = 2),
            PlaylistItem(id = 12, playlistId = 1, fsId = 300, fileName = "c.mp4", mediaType = 1),
        )
        coEvery { playlistRepository.getPlaylistByIdSync(1) } returns playlist
        coEvery { playlistRepository.getPlaylistItemsSync(1) } returns items

        val session = factory().fromDatabasePlaylist(1, 200L)

        assertEquals(1, session.startIndex)
        assertEquals(200L, session.items[session.startIndex].fsId)
    }

    @Test
    fun fromDatabasePlaylist_playlistNotFound_throws() = runTest {
        coEvery { playlistRepository.getPlaylistByIdSync(99) } returns null

        val ex = assertThrows(PlaybackSessionException::class.java) {
            kotlinx.coroutines.runBlocking { factory().fromDatabasePlaylist(99, null) }
        }
        assertEquals("播放列表不存在", ex.message)
    }

    @Test
    fun fromDatabasePlaylist_emptyItems_throws() = runTest {
        val playlist = Playlist(id = 1, name = "空列表", mediaType = 1)
        coEvery { playlistRepository.getPlaylistByIdSync(1) } returns playlist
        coEvery { playlistRepository.getPlaylistItemsSync(1) } returns emptyList()

        val ex = assertThrows(PlaybackSessionException::class.java) {
            kotlinx.coroutines.runBlocking { factory().fromDatabasePlaylist(1, null) }
        }
        assertEquals("播放列表为空", ex.message)
    }

    @Test
    fun fromDatabasePlaylist_mediaTypeMapping_imagePlaylist() = runTest {
        val playlist = Playlist(id = 1, name = "图片列表", mediaType = 2)
        val items = listOf(PlaylistItem(id = 10, playlistId = 1, fsId = 100, fileName = "a.jpg", mediaType = 2))
        coEvery { playlistRepository.getPlaylistByIdSync(1) } returns playlist
        coEvery { playlistRepository.getPlaylistItemsSync(1) } returns items

        val session = factory().fromDatabasePlaylist(1, null)

        assertEquals(MediaType.IMAGE.code, session.mediaType)
        assertEquals(3, session.items[0].category) // image
    }

    @Test
    fun fromDatabasePlaylist_mediaTypeMapping_mixedPlaylist() = runTest {
        val playlist = Playlist(id = 1, name = "混合", mediaType = 0)
        val items = listOf(PlaylistItem(id = 10, playlistId = 1, fsId = 100, fileName = "a.mp4", mediaType = 1))
        coEvery { playlistRepository.getPlaylistByIdSync(1) } returns playlist
        coEvery { playlistRepository.getPlaylistItemsSync(1) } returns items

        val session = factory().fromDatabasePlaylist(1, null)

        assertEquals(MediaType.ALL.code, session.mediaType)
    }

    // ==================================================================
    // 7.2-7.3: 最近播放历史
    // ==================================================================

    @Test
    fun fromRecentHistory_validHistory_returnsSessionWithCorrectItems() = runTest {
        val histories = listOf(
            history(id = 11, name = "a.mp4", fsId = 1),
            history(id = 12, name = "b.mp4", fsId = 2),
            history(id = 13, name = "c.mp4", fsId = 3),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(12) } returns MutableStateFlow(histories[1])

        val session = factory().fromRecentHistory(12)

        assertEquals(PlaybackSource.RECENT_HISTORY, session.source)
        assertEquals("history-12", session.playlistId)
        assertEquals(3, session.items.size)
        assertEquals(listOf(1L, 2L, 3L), session.items.map { it.fsId })
        assertEquals(1, session.startIndex)
        assertEquals(2L, session.items[session.startIndex].fsId)
        assertEquals(MediaType.ALL.code, session.mediaType)
        assertEquals("最近播放", session.folderPath)
        assertEquals("最近播放", session.title)
        assertEquals(null, session.sourcePlaylistId)
        assertEquals(null, session.sourceFolderPath)
    }

    @Test
    fun fromRecentHistory_emptyHistory_throwsError() = runTest {
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(emptyList())

        val ex = assertThrows(PlaybackSessionException::class.java) {
            kotlinx.coroutines.runBlocking { factory().fromRecentHistory(1) }
        }
        assertEquals("播放历史为空", ex.message)
    }

    @Test
    fun fromRecentHistory_fsIdLeqZero_filteredFromSnapshot() = runTest {
        val histories = listOf(
            history(id = 11, name = "a.mp4", fsId = 1),
            PlaybackHistory(
                id = 12, folderPath = "/movies/invalid", folderName = "invalid",
                mediaType = MediaType.VIDEO.code, fileCount = 1, fsId = 0,
            ),
            history(id = 13, name = "b.mp4", fsId = 2),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(11) } returns MutableStateFlow(histories[0])

        val session = factory().fromRecentHistory(11)

        assertEquals(2, session.items.size)
        assertEquals(listOf(1L, 2L), session.items.map { it.fsId })
    }

    @Test
    fun fromRecentHistory_targetNotInSnapshot_throwsError() = runTest {
        val histories = listOf(
            history(id = 11, name = "a.mp4", fsId = 1),
            history(id = 12, name = "b.mp4", fsId = 2),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        val clickedHistory = PlaybackHistory(
            id = 99, folderPath = "/movies/c.mp4", folderName = "c.mp4",
            mediaType = MediaType.VIDEO.code, fileCount = 1, fsId = 999,
        )
        every { historyRepository.getHistoryById(99) } returns MutableStateFlow(clickedHistory)

        val ex = assertThrows(PlaybackSessionException::class.java) {
            kotlinx.coroutines.runBlocking { factory().fromRecentHistory(99) }
        }
        assertEquals("该记录不在最近播放中", ex.message)
    }

    @Test
    fun fromRecentHistory_clickedHistoryNotFound_throwsError() = runTest {
        val histories = listOf(history(id = 11, name = "a.mp4", fsId = 1))
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(999) } returns MutableStateFlow(null)

        val ex = assertThrows(PlaybackSessionException::class.java) {
            kotlinx.coroutines.runBlocking { factory().fromRecentHistory(999) }
        }
        assertEquals("该记录不在最近播放中", ex.message)
    }

    @Test
    fun fromRecentHistory_exactly100Items_allIncludedInSnapshot() = runTest {
        val histories = (1..100).map { i ->
            history(id = i.toLong(), name = "file$i.mp4", fsId = i.toLong())
        }
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(50) } returns MutableStateFlow(histories[49])

        val session = factory().fromRecentHistory(50)

        assertEquals(100, session.items.size)
        assertEquals(49, session.startIndex)
        assertEquals(50L, session.items[session.startIndex].fsId)
    }

    @Test
    fun fromRecentHistory_moreThan100Items_passesLimitToRepository() = runTest {
        // 验证工厂使用 MAX_HISTORY 常量查询，不自行截断
        val histories = (1..100).map { i ->
            history(id = i.toLong(), name = "file$i.mp4", fsId = i.toLong())
        }
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(1) } returns MutableStateFlow(histories[0])

        val session = factory().fromRecentHistory(1)

        assertEquals(100, session.items.size)
    }

    @Test
    fun fromRecentHistory_chinesePaths_preservedInSession() = runTest {
        val histories = listOf(
            PlaybackHistory(
                id = 1,
                folderPath = "/网盘/中文目录/中文文件.mp4",
                folderName = "中文文件.mp4",
                mediaType = MediaType.VIDEO.code,
                fileCount = 1,
                fsId = 100,
            ),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(1) } returns MutableStateFlow(histories[0])

        val session = factory().fromRecentHistory(1)

        assertEquals(1, session.items.size)
        assertEquals("/网盘/中文目录/中文文件.mp4", session.items[0].path)
        assertEquals("中文文件.mp4", session.items[0].serverFilename)
    }

    @Test
    fun fromRecentHistory_allFsIdZero_throwsEmptyError() = runTest {
        val histories = listOf(
            PlaybackHistory(
                id = 1, folderPath = "/a", folderName = "a",
                mediaType = MediaType.VIDEO.code, fileCount = 1, fsId = 0,
            ),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)

        val ex = assertThrows(PlaybackSessionException::class.java) {
            kotlinx.coroutines.runBlocking { factory().fromRecentHistory(1) }
        }
        assertEquals("播放历史为空", ex.message)
    }

    @Test
    fun fromRecentHistory_imageHistory_mapsToImageCategory() = runTest {
        val histories = listOf(
            PlaybackHistory(
                id = 1,
                folderPath = "/photos/a.jpg",
                folderName = "a.jpg",
                mediaType = MediaType.IMAGE.code,
                fileCount = 1,
                fsId = 100,
            ),
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns
            MutableStateFlow(histories)
        every { historyRepository.getHistoryById(1) } returns MutableStateFlow(histories[0])

        val session = factory().fromRecentHistory(1)

        assertEquals(3, session.items[0].category) // image category
    }

    @Test
    fun fromRecentHistory_singleSnapshotNotReorderedBySubsequentFlowEmission() = runTest {
        // 会话创建后，即使 Flow 后续发射新值，session.items 不变
        val historyFlow = MutableStateFlow(
            listOf(history(id = 11, name = "a.mp4", fsId = 1), history(id = 12, name = "b.mp4", fsId = 2))
        )
        every { historyRepository.getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY) } returns historyFlow
        every { historyRepository.getHistoryById(11) } returns MutableStateFlow(
            history(id = 11, name = "a.mp4", fsId = 1)
        )

        val session = factory().fromRecentHistory(11)

        // 模拟数据库热流变化
        historyFlow.value = listOf(
            history(id = 13, name = "new.mp4", fsId = 3),
            history(id = 11, name = "a.mp4", fsId = 1),
            history(id = 12, name = "b.mp4", fsId = 2),
        )

        // session.items 不受影响
        assertEquals(listOf(1L, 2L), session.items.map { it.fsId })
    }
}
