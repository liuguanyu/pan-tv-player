package com.baidu.tv.player.kt.repository

import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ThumbnailProvider 测试（Task 10.5）。
 *
 * 验证缩略图数据边界：
 * - token 为空返回空 Map
 * - fsId 为 0 的文件被过滤
 * - 委托 FileRepository.fetchThumbnails
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ThumbnailProviderTest {

    @Test
    fun fetchThumbnails_emptyToken_returnsEmptyMap() = runTest {
        val authService = mockk<BaiduAuthService>()
        every { authService.getAccessToken() } returns null
        val fileRepository = mockk<FileRepository>()
        val provider = FileRepositoryThumbnailProvider(authService, fileRepository)

        val result = provider.fetchThumbnails(listOf(file("a.mp4", 1)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchThumbnails_allZeroFsIds_returnsEmptyMap() = runTest {
        val authService = mockk<BaiduAuthService>()
        every { authService.getAccessToken() } returns "token"
        val fileRepository = mockk<FileRepository>()
        val provider = FileRepositoryThumbnailProvider(authService, fileRepository)

        val result = provider.fetchThumbnails(listOf(file("a.mp4", 0), file("b.mp4", 0)))

        assertTrue(result.isEmpty())
    }

    @Test
    fun fetchThumbnails_delegatesToFileRepository() = runTest {
        val authService = mockk<BaiduAuthService>()
        every { authService.getAccessToken() } returns "token"
        val fileRepository = mockk<FileRepository>()
        coEvery { fileRepository.fetchThumbnails("token", listOf(1L, 2L)) } returns
            mapOf(1L to "https://thumb/1", 2L to "https://thumb/2")
        val provider = FileRepositoryThumbnailProvider(authService, fileRepository)

        val result = provider.fetchThumbnails(listOf(file("a.mp4", 1), file("b.mp4", 2)))

        assertEquals(mapOf(1L to "https://thumb/1", 2L to "https://thumb/2"), result)
    }

    @Test
    fun fetchThumbnails_filtersZeroFsIdsBeforeDelegating() = runTest {
        val authService = mockk<BaiduAuthService>()
        every { authService.getAccessToken() } returns "token"
        val fileRepository = mockk<FileRepository>()
        coEvery { fileRepository.fetchThumbnails("token", listOf(1L)) } returns mapOf(1L to "https://thumb/1")
        val provider = FileRepositoryThumbnailProvider(authService, fileRepository)

        val result = provider.fetchThumbnails(listOf(file("a.mp4", 1), file("b.mp4", 0)))

        assertEquals(mapOf(1L to "https://thumb/1"), result)
    }

    @Test
    fun fetchThumbnails_emptyFileList_returnsEmptyMap() = runTest {
        val authService = mockk<BaiduAuthService>()
        every { authService.getAccessToken() } returns "token"
        val fileRepository = mockk<FileRepository>()
        val provider = FileRepositoryThumbnailProvider(authService, fileRepository)

        val result = provider.fetchThumbnails(emptyList())

        assertTrue(result.isEmpty())
    }

    private fun file(name: String, fsId: Long) =
        FileInfo(fsId = fsId, path = "/movies/$name", serverFilename = name, category = 1)
}
