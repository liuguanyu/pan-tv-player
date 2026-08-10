package com.baidu.tv.player.kt.repository

import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.FileListResponse
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.network.FakeBaiduPanService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FileRepository 单测：分页 / 过滤 / 递归 / 非递归 / 文件详情。
 * 用 [FakeBaiduPanService] 隔离网络。
 */
class FileRepositoryTest {

    private fun fileInfo(name: String, isdir: Int = 0, category: Int = 0, path: String = "/$name") =
        FileInfo(serverFilename = name, isdir = isdir, category = category, path = path, fsId = name.hashCode().toLong())

    private fun resp(vararg files: FileInfo, errno: Int = 0): FileListResponse =
        FileListResponse(errno = errno, list = files.toList())

    @Test
    fun getFileList_filtersByMediaType_video() = runTest {
        val fake = FakeBaiduPanService().apply {
            enqueueFileList(
                resp(fileInfo("a.mp4", category = 1), fileInfo("b.jpg", category = 3), fileInfo("c.mkv", category = 1)),
            )
        }
        val repo = FileRepository(fake)
        val result = repo.getFileList("token", "/dir", MediaType.VIDEO.code)
        assertEquals(listOf("a.mp4", "c.mkv"), result.map { it.serverFilename })
    }

    @Test
    fun getFileList_keepsDirectoriesRegardlessOfType() = runTest {
        val fake = FakeBaiduPanService().apply {
            enqueueFileList(resp(fileInfo("dir1", isdir = 1), fileInfo("a.mp4", category = 1)))
        }
        val repo = FileRepository(fake)
        val result = repo.getFileList("token", "/dir", MediaType.IMAGE.code)
        // 目录始终保留
        assertEquals(listOf("dir1"), result.map { it.serverFilename })
    }

    @Test
    fun getFileList_paginatesUntilPageSmallerThanLimit() = runTest {
        val fake = FakeBaiduPanService()
        // 两满页（各 1000）+ 一小页（3），PAGE_LIMIT=1000，size<limit 时结束。
        // 用 .mp4 扩展名确保通过 MediaType.ALL 过滤。
        val fullPage = resp(*Array(1000) { fileInfo("f$it.mp4", category = 1) })
        val lastPage = resp(fileInfo("a.mp4", category = 1), fileInfo("b.mp4", category = 1), fileInfo("c.mp4", category = 1))
        fake.enqueueFileList(fullPage, fullPage, lastPage)
        val repo = FileRepository(fake)
        val result = repo.getFileList("token", "/dir", MediaType.ALL.code)
        assertEquals(2003, result.size)
        // 分页参数 start 递增 1000
        assertEquals(listOf(0, 1000, 2000), fake.fileListCalls.map { it.start })
    }

    @Test
    fun getFileList_capsAtMaxPages() = runTest {
        val fake = FakeBaiduPanService()
        // 每页都满（size == PAGE_LIMIT=1000），最多 maxPages=2 页
        val fullPage = resp(*Array(1000) { fileInfo("f$it.mp4", category = 1) })
        fake.enqueueFileList(fullPage, fullPage, fullPage)
        val repo = FileRepository(fake)
        val result = repo.getFileList("token", "/dir", MediaType.ALL.code, maxPages = 2)
        assertEquals(2000, result.size)
        // 只调用了 2 次
        assertEquals(2, fake.fileListCalls.size)
    }

    @Test(expected = IllegalStateException::class)
    fun getFileList_throwsOnApiError() = runTest {
        val fake = FakeBaiduPanService().apply {
            enqueueFileList(resp(errno = -1).copy(errmsg = "denied"))
        }
        val repo = FileRepository(fake)
        repo.getFileList("token", "/dir", MediaType.ALL.code)
        Unit
    }

    @Test
    fun fetchFilesNonRecursive_loadsAllPages_noMaxPageCap() = runTest {
        val fake = FakeBaiduPanService()
        val fullPage = resp(*Array(1000) { fileInfo("f$it") })
        val lastPage = resp(fileInfo("last"))
        fake.enqueueFileList(fullPage, lastPage)
        val repo = FileRepository(fake)
        val result = repo.fetchFilesNonRecursive("token", "/dir")
        assertEquals(1001, result.size)
    }

    @Test
    fun fetchFilesRecursive_walksSubdirectories() = runTest {
        val fake = FakeBaiduPanService()
        // 根目录：1 个文件 + 1 个子目录
        fake.enqueueFileList(resp(fileInfo("root.mp4", category = 1), fileInfo("sub", isdir = 1, path = "/dir/sub")))
        // 子目录：1 个文件
        fake.enqueueFileList(resp(fileInfo("child.mp4", category = 1)))
        val repo = FileRepository(fake)
        val result = repo.fetchFilesRecursive("token", "/dir")
        // 目录不进结果，仅文件
        assertEquals(listOf("root.mp4", "child.mp4"), result.map { it.serverFilename })
    }

    @Test
    fun fetchFilesRecursive_capsAtMaxDirs() = runTest {
        val fake = FakeBaiduPanService()
        // 根目录产生一个子目录，子目录又产生子目录……形成链，但受 maxDirs=2 限制
        fake.enqueueFileList(
            resp(fileInfo("root.mp4", category = 1), fileInfo("sub1", isdir = 1, path = "/dir/sub1")),
        )
        fake.enqueueFileList(
            resp(fileInfo("sub1.mp4", category = 1), fileInfo("sub2", isdir = 1, path = "/dir/sub2")),
        )
        // 仍有未处理目录，但应被 maxDirs 截断（不请求第三页）
        val repo = FileRepository(fake)
        val result = repo.fetchFilesRecursive("token", "/dir", maxDirs = 2)
        assertTrue(result.any { it.serverFilename == "root.mp4" })
        assertTrue(result.any { it.serverFilename == "sub1.mp4" })
        // 只调用了 2 次（maxDirs=2）
        assertEquals(2, fake.fileListCalls.size)
    }

    @Test
    fun getFileListRecursive_usesListallEndpoint() = runTest {
        val fake = FakeBaiduPanService().apply {
            enqueueRecursive(resp(fileInfo("a.mp4", category = 1), fileInfo("b.jpg", category = 3)))
        }
        val repo = FileRepository(fake)
        val result = repo.getFileListRecursive("token", "/dir", MediaType.VIDEO.code)
        assertEquals(listOf("a.mp4"), result.map { it.serverFilename })
        assertEquals(1, fake.recursiveCalls.size)
        assertEquals("/dir", fake.recursiveCalls[0].dir)
    }

    @Test
    fun fetchFileDetail_returnsFirstItem() = runTest {
        val target = fileInfo("target.mp4", category = 1)
        val fake = FakeBaiduPanService().apply {
            fileInfoResponse = resp(target)
        }
        val repo = FileRepository(fake)
        val detail = repo.fetchFileDetail("token", 123L)
        assertEquals("target.mp4", detail?.serverFilename)
    }

    @Test
    fun fetchFileDetail_returnsNullOnApiError() = runTest {
        val fake = FakeBaiduPanService().apply {
            fileInfoResponse = resp(errno = -1)
        }
        val repo = FileRepository(fake)
        assertEquals(null, repo.fetchFileDetail("token", 123L))
    }

    @Test
    fun filterFiles_emptyInput_returnsEmpty() {
        val repo = FileRepository(FakeBaiduPanService())
        assertEquals(emptyList<FileInfo>(), repo.filterFiles(null, MediaType.ALL.code))
        assertEquals(emptyList<FileInfo>(), repo.filterFiles(emptyList(), MediaType.ALL.code))
    }
}
