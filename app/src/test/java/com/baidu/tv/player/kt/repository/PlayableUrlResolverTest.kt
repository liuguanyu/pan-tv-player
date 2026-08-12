package com.baidu.tv.player.kt.repository

import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PlayableUrlResolverTest {

    private lateinit var authService: BaiduAuthService
    private lateinit var fileRepository: FileRepository

    @Before
    fun setUp() {
        authService = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
    }

    @Test
    fun resolve_tokenEmpty_throwsIllegalState() = runTest {
        every { authService.getAccessToken() } returns null
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val error = runCatching { resolver.resolve(1L, "https://d", "a.mp4") }
            .exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("未获取到访问令牌，请先登录", error!!.message)
    }

    @Test
    fun resolve_dlinkHasToken_returnsAsIs() = runTest {
        every { authService.getAccessToken() } returns "tk"
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val url = resolver.resolve(1L, "https://d?access_token=tk", "a.mp4")
        assertEquals("https://d?access_token=tk", url)
    }

    @Test
    fun resolve_dlinkHasQuery_appendsWithAmpersand() = runTest {
        every { authService.getAccessToken() } returns "tk"
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val url = resolver.resolve(1L, "https://d?foo=bar", "a.mp4")
        assertEquals("https://d?foo=bar&access_token=tk", url)
    }

    @Test
    fun resolve_dlinkNoQuery_appendsWithQuestionMark() = runTest {
        every { authService.getAccessToken() } returns "tk"
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val url = resolver.resolve(1L, "https://d", "a.mp4")
        assertEquals("https://d?access_token=tk", url)
    }

    @Test
    fun resolve_dlinkNull_detailHasDlink_returnsUrlWithToken() = runTest {
        every { authService.getAccessToken() } returns "tk"
        coEvery { fileRepository.fetchFileDetail("tk", 1L) } returns
            FileInfo(fsId = 1L, serverFilename = "a.mp4", dlink = "https://detail/d")
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val url = resolver.resolve(1L, dlink = null, serverFilename = "a.mp4")
        assertEquals("https://detail/d?access_token=tk", url)
    }

    @Test
    fun resolve_dlinkNull_detailNull_throwsIllegalState() = runTest {
        every { authService.getAccessToken() } returns "tk"
        coEvery { fileRepository.fetchFileDetail("tk", 1L) } returns null
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val error = runCatching { resolver.resolve(1L, dlink = null, serverFilename = "a.mp4") }
            .exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("文件缺少 dlink: a.mp4", error!!.message)
    }

    @Test
    fun resolve_dlinkNull_detailDlinkNull_throwsIllegalState() = runTest {
        every { authService.getAccessToken() } returns "tk"
        coEvery { fileRepository.fetchFileDetail("tk", 1L) } returns
            FileInfo(fsId = 1L, serverFilename = "a.mp4", dlink = null)
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val error = runCatching { resolver.resolve(1L, dlink = null, serverFilename = "a.mp4") }
            .exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("文件缺少 dlink: a.mp4", error!!.message)
    }

    @Test
    fun resolve_dlinkNull_fetchFails_throws() = runTest {
        every { authService.getAccessToken() } returns "tk"
        coEvery { fileRepository.fetchFileDetail("tk", 1L) } throws IOException("network error")
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val error = runCatching { resolver.resolve(1L, dlink = null, serverFilename = "a.mp4") }
            .exceptionOrNull()
        assertTrue(error is IOException)
        assertEquals("network error", error!!.message)
    }

    @Test
    fun resolve_cancellation_propagates() = runTest {
        every { authService.getAccessToken() } returns "tk"
        val cancellation = CancellationException("cancelled")
        coEvery { fileRepository.fetchFileDetail("tk", 1L) } throws cancellation
        val resolver = PlayableUrlResolver(authService, fileRepository)

        val error = runCatching { resolver.resolve(1L, dlink = null, serverFilename = "a.mp4") }
            .exceptionOrNull()
        // CancellationException should propagate as-is, not be swallowed.
        assertTrue(error === cancellation)
    }
}
