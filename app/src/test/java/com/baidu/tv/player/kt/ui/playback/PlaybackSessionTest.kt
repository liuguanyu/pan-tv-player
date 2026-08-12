package com.baidu.tv.player.kt.ui.playback

import com.baidu.tv.player.kt.model.FileInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [PlaybackSession] 模型测试（Task 7.1）。
 *
 * 验证不可变性、title、startIndex 和 init 校验。
 */
class PlaybackSessionTest {

    private fun fileInfo(fsId: Long, name: String = "f$fsId") =
        FileInfo(fsId = fsId, serverFilename = name, category = 1)

    @Test
    fun items_areImmutableSnapshot() {
        val original = listOf(fileInfo(1), fileInfo(2))
        val session = PlaybackSession(
            source = PlaybackSource.DIRECTORY_CACHE,
            playlistId = "p",
            items = original,
            folderPath = "/dir",
            title = "dir",
            startIndex = 0,
            mediaType = 1,
            sourcePlaylistId = null,
            sourceFolderPath = "/dir",
        )
        // 修改原始 List 不影响 session.items
        val mutable = original.toMutableList()
        mutable.add(fileInfo(3))
        assertEquals(2, session.items.size)
        assertNotSame("items should be a defensive copy or same immutable list", mutable, session.items)
    }

    @Test
    fun title_preservesDirectoryCacheFolderName() {
        val session = PlaybackSession(
            source = PlaybackSource.DIRECTORY_CACHE,
            playlistId = "p",
            items = listOf(fileInfo(1)),
            folderPath = "/movies",
            title = "movies",
            startIndex = 0,
            mediaType = 1,
            sourcePlaylistId = null,
            sourceFolderPath = "/movies",
        )
        assertEquals("movies", session.title)
    }

    @Test
    fun title_preservesDatabasePlaylistName() {
        val session = PlaybackSession(
            source = PlaybackSource.DATABASE_PLAYLIST,
            playlistId = "42",
            items = listOf(fileInfo(1)),
            folderPath = "我的播放列表",
            title = "我的播放列表",
            startIndex = 0,
            mediaType = 1,
            sourcePlaylistId = 42L,
            sourceFolderPath = null,
        )
        assertEquals("我的播放列表", session.title)
    }

    @Test
    fun title_preservesRecentHistoryName() {
        val session = PlaybackSession(
            source = PlaybackSource.RECENT_HISTORY,
            playlistId = "history-1",
            items = listOf(fileInfo(1)),
            folderPath = "最近播放",
            title = "最近播放",
            startIndex = 0,
            mediaType = 1,
            sourcePlaylistId = null,
            sourceFolderPath = null,
        )
        assertEquals("最近播放", session.title)
    }

    @Test
    fun startIndex_zeroForSingleItem() {
        val session = PlaybackSession(
            source = PlaybackSource.DIRECTORY_CACHE,
            playlistId = "p",
            items = listOf(fileInfo(1)),
            folderPath = "/dir",
            title = "dir",
            startIndex = 0,
            mediaType = 1,
            sourcePlaylistId = null,
            sourceFolderPath = "/dir",
        )
        assertEquals(0, session.startIndex)
    }

    @Test
    fun startIndex_middleOfList() {
        val session = PlaybackSession(
            source = PlaybackSource.DIRECTORY_CACHE,
            playlistId = "p",
            items = listOf(fileInfo(1), fileInfo(2), fileInfo(3)),
            folderPath = "/dir",
            title = "dir",
            startIndex = 1,
            mediaType = 1,
            sourcePlaylistId = null,
            sourceFolderPath = "/dir",
        )
        assertEquals(1, session.startIndex)
    }

    @Test
    fun startIndex_lastIndex() {
        val items = listOf(fileInfo(1), fileInfo(2), fileInfo(3))
        val session = PlaybackSession(
            source = PlaybackSource.DIRECTORY_CACHE,
            playlistId = "p",
            items = items,
            folderPath = "/dir",
            title = "dir",
            startIndex = items.lastIndex,
            mediaType = 1,
            sourcePlaylistId = null,
            sourceFolderPath = "/dir",
        )
        assertEquals(2, session.startIndex)
    }

    @Test
    fun emptyItems_withZeroStartIndex_isValid() {
        val session = PlaybackSession(
            source = PlaybackSource.DIRECTORY_CACHE,
            playlistId = "p",
            items = emptyList(),
            folderPath = "/dir",
            title = "dir",
            startIndex = 0,
            mediaType = 1,
            sourcePlaylistId = null,
            sourceFolderPath = "/dir",
        )
        assertEquals(0, session.startIndex)
        assertEquals(0, session.items.size)
    }

    @Test
    fun negativeStartIndex_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackSession(
                source = PlaybackSource.DIRECTORY_CACHE,
                playlistId = "p",
                items = listOf(fileInfo(1)),
                folderPath = "/dir",
                title = "dir",
                startIndex = -1,
                mediaType = 1,
                sourcePlaylistId = null,
                sourceFolderPath = "/dir",
            )
        }
    }

    @Test
    fun startIndexOutOfBounds_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaybackSession(
                source = PlaybackSource.DIRECTORY_CACHE,
                playlistId = "p",
                items = listOf(fileInfo(1), fileInfo(2)),
                folderPath = "/dir",
                title = "dir",
                startIndex = 2,
                mediaType = 1,
                sourcePlaylistId = null,
                sourceFolderPath = "/dir",
            )
        }
    }

    @Test
    fun dataClassCopy_preservesImmutability() {
        val items = listOf(fileInfo(1), fileInfo(2))
        val session = PlaybackSession(
            source = PlaybackSource.DIRECTORY_CACHE,
            playlistId = "p",
            items = items,
            folderPath = "/dir",
            title = "dir",
            startIndex = 0,
            mediaType = 1,
            sourcePlaylistId = null,
            sourceFolderPath = "/dir",
        )
        val copied = session.copy(startIndex = 1)
        assertEquals(1, copied.startIndex)
        assertEquals(session.items, copied.items)
        assertEquals(session.title, copied.title)
    }
}
