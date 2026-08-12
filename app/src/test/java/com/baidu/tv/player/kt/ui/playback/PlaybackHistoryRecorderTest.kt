package com.baidu.tv.player.kt.ui.playback

import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.util.MainCoroutineRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PlaybackHistoryRecorder 单测（Task 11.1 映射 & 11.2 写入行为）。
 *
 * 11.1 映射：
 * - 路径回退：file.path 为空 → folderPath + serverFilename
 * - 媒体类型：视频 → VIDEO.code，图片 → IMAGE.code
 * - 来源上下文：sourcePlaylistId / sourceFolderPath 取自 state
 * - 封面候选优先级：icon → url1 → url2 → url3，跳过含 `/file/` 的 URL
 *
 * 11.2 写入行为：
 * - Room 异常不中断播放（捕获并记录日志）
 * - CancellationException 传播（不被当作普通异常吞掉）
 * - 相同路径去重由 repository upsert 保证，recorder 只需正确调用 insert
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackHistoryRecorderTest {

    @get:Rule
    val coroutineRule = MainCoroutineRule()

    private lateinit var historyRepository: PlaybackHistoryRepository
    private lateinit var recorder: PlaybackHistoryRecorder

    @Before
    fun setUp() {
        historyRepository = mockk(relaxed = true)
        recorder = PlaybackHistoryRecorder(historyRepository)
    }

    private fun video(
        name: String = "a.mp4",
        fsId: Long = 1L,
        path: String? = "/movies/$name",
        thumbs: FileInfo.Thumbs? = null,
    ) = FileInfo(
        fsId = fsId,
        path = path,
        serverFilename = name,
        category = 1,
        thumbs = thumbs,
    )

    private fun image(
        name: String = "a.jpg",
        fsId: Long = 2L,
        path: String? = "/photos/$name",
        thumbs: FileInfo.Thumbs? = null,
    ) = FileInfo(
        fsId = fsId,
        path = path,
        serverFilename = name,
        category = 3,
        thumbs = thumbs,
    )

    private fun state(
        folderPath: String = "/movies",
        sourcePlaylistId: Long? = 7L,
        sourceFolderPath: String? = "/movies",
    ) = PlaybackUiState(
        folderPath = folderPath,
        sourcePlaylistId = sourcePlaylistId,
        sourceFolderPath = sourceFolderPath,
    )

    private fun thumbs(
        icon: String? = null,
        url1: String? = null,
        url2: String? = null,
        url3: String? = null,
    ) = FileInfo.Thumbs(icon = icon, url1 = url1, url2 = url2, url3 = url3)

    // ------------------------------------------------------------------
    // Task 11.1: 映射测试
    // ------------------------------------------------------------------

    @Test
    fun map_pathFallback_filePathNull_usesFolderPlusServerFilename() {
        val file = video(path = null, name = "a.mp4")
        val state = state(folderPath = "/movies")
        val history = recorder.mapToHistory(file, state, fileDetail = null)
        assertNotNull(history)
        assertEquals("/movies/a.mp4", history!!.folderPath)
        assertEquals("a.mp4", history.folderName)
    }

    @Test
    fun map_pathFallback_emptyFolderPath_prependsRootSlash() {
        val file = video(path = null, name = "a.mp4")
        val state = state(folderPath = "")
        val history = recorder.mapToHistory(file, state, fileDetail = null)
        assertEquals("/a.mp4", history!!.folderPath)
    }

    @Test
    fun map_pathFallback_folderPathWithTrailingSlash_isTrimmed() {
        val file = video(path = null, name = "a.mp4")
        val state = state(folderPath = "/movies/")
        val history = recorder.mapToHistory(file, state, fileDetail = null)
        assertEquals("/movies/a.mp4", history!!.folderPath)
    }

    @Test
    fun map_pathFallback_filePathBlank_usesFolderPlusServerFilename() {
        val file = video(path = "   ", name = "a.mp4")
        val state = state(folderPath = "/movies")
        val history = recorder.mapToHistory(file, state, fileDetail = null)
        assertEquals("/movies/a.mp4", history!!.folderPath)
    }

    @Test
    fun map_bothPathAndServerFilenameNull_returnsNull() {
        val file = FileInfo(fsId = 1L, path = null, serverFilename = null, category = 1)
        val state = state(folderPath = "/movies")
        val history = recorder.mapToHistory(file, state, fileDetail = null)
        assertNull(history)
    }

    @Test
    fun map_filePathPresent_ignoresFolderPath() {
        val file = video(path = "/custom/a.mp4", name = "a.mp4")
        val state = state(folderPath = "/different")
        val history = recorder.mapToHistory(file, state, fileDetail = null)
        assertEquals("/custom/a.mp4", history!!.folderPath)
    }

    @Test
    fun map_mediaType_video_mapsToVideoCode() {
        val file = video()
        val history = recorder.mapToHistory(file, state(), fileDetail = null)
        assertEquals(MediaType.VIDEO.code, history!!.mediaType)
    }

    @Test
    fun map_mediaType_image_mapsToImageCode() {
        val file = image()
        val history = recorder.mapToHistory(file, state(folderPath = "/photos"), fileDetail = null)
        assertEquals(MediaType.IMAGE.code, history!!.mediaType)
    }

    @Test
    fun map_sourceContext_copiedFromState() {
        val file = video()
        val state = state(sourcePlaylistId = 42L, sourceFolderPath = "/library/movies")
        val history = recorder.mapToHistory(file, state, fileDetail = null)!!
        assertEquals(42L, history.sourcePlaylistId)
        assertEquals("/library/movies", history.sourceFolderPath)
    }

    @Test
    fun map_sourceContext_nullWhenStateHasNull() {
        val file = video()
        val state = state(sourcePlaylistId = null, sourceFolderPath = null)
        val history = recorder.mapToHistory(file, state, fileDetail = null)!!
        assertNull(history.sourcePlaylistId)
        assertNull(history.sourceFolderPath)
    }

    @Test
    fun map_coverPriority_iconFirst() {
        val file = video(thumbs = thumbs(icon = "https://t/icon", url1 = "https://t/url1"))
        val history = recorder.mapToHistory(file, state(), fileDetail = null)!!
        assertEquals("https://t/icon", history.coverImagePath)
    }

    @Test
    fun map_coverPriority_iconNull_fallsBackToUrl1() {
        val file = video(thumbs = thumbs(url1 = "https://t/url1", url2 = "https://t/url2"))
        val history = recorder.mapToHistory(file, state(), fileDetail = null)!!
        assertEquals("https://t/url1", history.coverImagePath)
    }

    @Test
    fun map_coverPriority_url1Null_fallsBackToUrl2() {
        val file = video(thumbs = thumbs(url2 = "https://t/url2", url3 = "https://t/url3"))
        val history = recorder.mapToHistory(file, state(), fileDetail = null)!!
        assertEquals("https://t/url2", history.coverImagePath)
    }

    @Test
    fun map_coverPriority_url2Null_fallsBackToUrl3() {
        val file = video(thumbs = thumbs(url3 = "https://t/url3"))
        val history = recorder.mapToHistory(file, state(), fileDetail = null)!!
        assertEquals("https://t/url3", history.coverImagePath)
    }

    @Test
    fun map_coverPriority_skipsUrlsContainingFilePlaceholder() {
        // icon 含 /file/ → 跳过，url1 为空 → url2 可用
        val file = video(thumbs = thumbs(icon = "https://t/file/placeholder", url2 = "https://t/url2"))
        val history = recorder.mapToHistory(file, state(), fileDetail = null)!!
        assertEquals("https://t/url2", history.coverImagePath)
    }

    @Test
    fun map_coverPriority_allUrlsBlankOrFile_returnsNullCover() {
        val file = video(thumbs = thumbs(icon = "  ", url1 = "https://t/file/x", url2 = null, url3 = ""))
        val history = recorder.mapToHistory(file, state(), fileDetail = null)!!
        assertNull(history.coverImagePath)
    }

    @Test
    fun map_coverFallback_fileThumbsNull_usesFileDetailThumbs() {
        val file = video(thumbs = null)
        val fileDetail = video(thumbs = thumbs(url1 = "https://t/from-detail"))
        val history = recorder.mapToHistory(file, state(), fileDetail = fileDetail)!!
        assertEquals("https://t/from-detail", history.coverImagePath)
    }

    @Test
    fun map_coverFallback_fileThumbsPresent_ignoresFileDetailThumbs() {
        val file = video(thumbs = thumbs(icon = "https://t/from-file"))
        val fileDetail = video(thumbs = thumbs(icon = "https://t/from-detail"))
        val history = recorder.mapToHistory(file, state(), fileDetail = fileDetail)!!
        assertEquals("https://t/from-file", history.coverImagePath)
    }

    @Test
    fun map_fsIdAndFileCount_correctlyPopulated() {
        val file = video(fsId = 99L)
        val history = recorder.mapToHistory(file, state(), fileDetail = null)!!
        assertEquals(99L, history.fsId)
        assertEquals(1, history.fileCount)
    }

    @Test
    fun map_folderName_fallsBackToPathSubstringWhenServerFilenameNull() {
        val file = FileInfo(fsId = 1L, path = "/movies/a.mp4", serverFilename = null, category = 1)
        val history = recorder.mapToHistory(file, state(), fileDetail = null)!!
        assertEquals("a.mp4", history.folderName)
    }

    // ------------------------------------------------------------------
    // Task 11.2: 写入行为测试
    // ------------------------------------------------------------------

    @Test
    fun record_normalRoomException_doesNotInterruptPlayback() = runTest {
        val file = video()
        coEvery { historyRepository.insert(any()) } throws RuntimeException("Room 写库失败")

        val result = recorder.record(file, state(), fileDetail = null)

        assertFalse(result)
        // 关键：协程未抛异常，调用方可继续
    }

    @Test
    fun record_cancellationException_propagates() = runTest {
        val file = video()
        coEvery { historyRepository.insert(any()) } throws CancellationException("被取消")

        var caught: CancellationException? = null
        try {
            recorder.record(file, state(), fileDetail = null)
        } catch (e: CancellationException) {
            caught = e
        }
        assertNotNull("CancellationException 必须传播，不能被吞掉", caught)
    }

    @Test
    fun record_success_returnsTrueAndCallsInsert() = runTest {
        val file = video()
        coEvery { historyRepository.insert(any()) } returns Unit

        val result = recorder.record(file, state(), fileDetail = null)

        assertTrue(result)
        coVerify(exactly = 1) { historyRepository.insert(any()) }
    }

    @Test
    fun record_pathEmpty_skipsInsertAndReturnsFalse() = runTest {
        val file = FileInfo(fsId = 1L, path = null, serverFilename = null, category = 1)
        val result = recorder.record(file, state(), fileDetail = null)
        assertFalse(result)
        coVerify(exactly = 0) { historyRepository.insert(any()) }
    }

    @Test
    fun record_passesMappedHistoryToRepository() = runTest {
        val file = video(name = "a.mp4", fsId = 5L, path = "/movies/a.mp4")
        val state = state(sourcePlaylistId = 3L, sourceFolderPath = "/movies")
        val historySlot = slot<PlaybackHistory>()
        coEvery { historyRepository.insert(capture(historySlot)) } returns Unit

        recorder.record(file, state, fileDetail = null)
        advanceUntilIdle()

        val captured = historySlot.captured
        assertEquals("/movies/a.mp4", captured.folderPath)
        assertEquals("a.mp4", captured.folderName)
        assertEquals(MediaType.VIDEO.code, captured.mediaType)
        assertEquals(5L, captured.fsId)
        assertEquals(3L, captured.sourcePlaylistId)
        assertEquals("/movies", captured.sourceFolderPath)
    }

    @Test
    fun record_samePathDedup_delegateToRepositoryUpsert() = runTest {
        // 相同 folderPath 的两次 record 都调用 insert，由 repository 的 upsert 保证去重。
        val file = video(path = "/movies/a.mp4")
        coEvery { historyRepository.insert(any()) } returns Unit

        recorder.record(file, state(), fileDetail = null)
        recorder.record(file, state(), fileDetail = null)
        advanceUntilIdle()

        coVerify(exactly = 2) { historyRepository.insert(any()) }
    }

    @Test
    fun record_fileDetailUsedForCoverWhenFileThumbsNull() = runTest {
        val file = video(thumbs = null)
        val fileDetail = video(thumbs = thumbs(icon = "https://t/detail-icon"))
        val historySlot = slot<PlaybackHistory>()
        coEvery { historyRepository.insert(capture(historySlot)) } returns Unit

        recorder.record(file, state(), fileDetail = fileDetail)
        advanceUntilIdle()

        assertEquals("https://t/detail-icon", historySlot.captured.coverImagePath)
    }
}
