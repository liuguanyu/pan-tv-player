package com.baidu.tv.player.kt.repository

import android.util.Log
import com.baidu.tv.player.kt.di.PanApi
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.FileListResponse
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.network.BaiduPanService
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FileRepository"
private const val PAGE_LIMIT = 1000
private const val MAX_PAGES_DEFAULT = 5
private const val MAX_RECURSIVE_DIRS = 100

/**
 * 文件数据仓库。
 *
 * 全部为 `suspend` 函数，替代 Java 版 Callback + Thread.sleep 轮询。
 * 由 Hilt 注入 [BaiduPanService]（suspend Retrofit 方法天然支持协程取消）。
 */
@Singleton
open class FileRepository @Inject constructor(
    @PanApi private val apiService: BaiduPanService,
) {

    /**
     * 获取文件列表（智能分页，最多加载前 [maxPages] 页以避免内存溢出）。
     *
     * @return 过滤后的文件列表（按 [mediaType]）。当目录超过限制时返回前 5000 个。
     */
    open suspend fun getFileList(
        accessToken: String,
        dirPath: String,
        mediaType: Int,
        maxPages: Int = MAX_PAGES_DEFAULT,
    ): List<FileInfo> {
        Log.d(TAG, "开始获取文件列表: dirPath=$dirPath, mediaType=$mediaType")
        val (allFiles, hasMore) = fetchPagesWithLimit(accessToken, dirPath, maxPages)
        Log.d(TAG, "获取到文件数量: ${allFiles.size}, 还有更多: $hasMore")
        val filtered = filterFiles(allFiles, mediaType)
        Log.d(TAG, "过滤后文件数量: ${filtered.size}")
        if (hasMore) {
            Log.w(TAG, "目录包含超过5000个文件，仅显示前5000个以避免内存溢出")
        }
        return filtered
    }

    /**
     * 非递归获取单个目录的全部文件（不限制页数）。
     */
    open suspend fun fetchFilesNonRecursive(accessToken: String, dirPath: String): List<FileInfo> {
        Log.d(TAG, "fetchFilesNonRecursive开始: dirPath=$dirPath")
        val (allFiles, _) = fetchPagesWithLimit(accessToken, dirPath, Int.MAX_VALUE)
        Log.d(TAG, "fetchFilesNonRecursive完成: 文件数=${allFiles.size}")
        return allFiles
    }

    /**
     * 手动递归获取文件列表（深度优先，最多 [maxDirs] 个目录防止无限递归）。
     * 不依赖不稳定的 xpan/multimedia?method=listall 接口。
     */
    open suspend fun fetchFilesRecursive(
        accessToken: String,
        dirPath: String,
        maxDirs: Int = MAX_RECURSIVE_DIRS,
    ): List<FileInfo> {
        Log.d(TAG, "fetchFilesRecursive开始: dirPath=$dirPath")
        val allFiles = mutableListOf<FileInfo>()
        val pendingDirs = ArrayDeque<String>()
        pendingDirs.addLast(dirPath)
        var processed = 0

        while (pendingDirs.isNotEmpty() && processed < maxDirs) {
            val current = pendingDirs.removeFirst()
            val (files, _) = try {
                fetchPagesWithLimit(accessToken, current, MAX_PAGES_DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "获取目录 $current 失败: ${e.message}")
                // 根源目录失败必须上抛，让刷新流程保留旧列表；子目录失败则跳过并继续扫描。
                if (current == dirPath) throw e
                processed++
                continue
            }
            for (file in files) {
                if (file.isDirectory()) {
                    file.path?.let { pendingDirs.addLast(it) }
                } else {
                    allFiles.add(file)
                }
            }
            processed++
        }
        if (pendingDirs.isNotEmpty()) {
            Log.w(TAG, "已达到最大目录处理数限制: $maxDirs")
        }
        Log.d(TAG, "fetchFilesRecursive完成: 总文件数=${allFiles.size}")
        return allFiles
    }

    /**
     * 递归获取文件列表（基于 xpan/multimedia?method=listall，含过滤）。
     */
    open suspend fun getFileListRecursive(
        accessToken: String,
        dirPath: String,
        mediaType: Int,
    ): List<FileInfo> {
        Log.d(TAG, "开始递归获取文件列表: dirPath=$dirPath, mediaType=$mediaType")
        val (allFiles, _) = fetchPagesRecursiveWithLimit(accessToken, dirPath, Int.MAX_VALUE)
        val filtered = filterFiles(allFiles, mediaType)
        Log.d(TAG, "递归过滤后文件数量: ${filtered.size}")
        return filtered
    }

    /**
     * 获取单个文件详情（含 dlink）。返回首个匹配项，无结果时返回 null。
     */
    open suspend fun fetchFileDetail(accessToken: String, fsId: Long): FileInfo? {
        val fsids = "[$fsId]"
        val resp = apiService.getFileInfo("filemetas", fsids, 1, accessToken)
        if (!resp.isSuccess()) {
            Log.e(TAG, "获取文件详情失败: ${resp.errmsg}")
            return null
        }
        return resp.list?.firstOrNull()
    }

    /**
     * 智能分页加载（限制最大页数）。返回 (累积文件, 是否还有更多)。
     */
    private suspend fun fetchPagesWithLimit(
        accessToken: String,
        dirPath: String,
        maxPages: Int,
    ): Pair<List<FileInfo>, Boolean> {
        val accumulated = mutableListOf<FileInfo>()
        var start = 0
        var remaining = maxPages

        while (remaining > 0) {
            Log.d(TAG, "获取第 ${start / PAGE_LIMIT + 1} 页，start=$start, 剩余页数=$remaining")
            val resp = apiService.getFileList(
                "list", dirPath, "name", 0, start, PAGE_LIMIT, 1, 0, accessToken,
            )
            if (!resp.isSuccess()) {
                val errMsg = resp.errmsg?.takeIf { it.isNotEmpty() }
                    ?: "API返回错误，errno=${resp.errno}"
                Log.e(TAG, "API返回失败: $errMsg, errno=${resp.errno}")
                throw IllegalStateException(errMsg)
            }
            val page = resp.list ?: emptyList()
            if (page.isEmpty()) {
                Log.d(TAG, "当前页无文件，总共 ${accumulated.size} 个文件")
                return accumulated to false
            }
            accumulated.addAll(page)
            if (page.size < PAGE_LIMIT) {
                Log.d(TAG, "所有分页获取完成，总共 ${accumulated.size} 个文件")
                return accumulated to false
            }
            start += PAGE_LIMIT
            remaining--
        }
        Log.w(TAG, "达到最大页数限制，停止加载")
        return accumulated to true
    }

    /**
     * 递归模式分页加载（xpan/multimedia?method=listall）。返回 (累积文件, 是否还有更多)。
     */
    private suspend fun fetchPagesRecursiveWithLimit(
        accessToken: String,
        dirPath: String,
        maxPages: Int,
    ): Pair<List<FileInfo>, Boolean> {
        val accumulated = mutableListOf<FileInfo>()
        var start = 0
        var remaining = maxPages

        while (remaining > 0) {
            Log.d(TAG, "递归获取第 ${start / PAGE_LIMIT + 1} 页，start=$start, 剩余页数=$remaining")
            val resp = apiService.getFileListRecursive(
                "listall", dirPath, "name", 0, PAGE_LIMIT, 1, accessToken,
            )
            if (!resp.isSuccess()) {
                throw IllegalStateException(resp.errmsg ?: "API返回错误，errno=${resp.errno}")
            }
            val page = resp.list ?: emptyList()
            if (page.isEmpty()) {
                return accumulated to false
            }
            accumulated.addAll(page)
            if (page.size < PAGE_LIMIT) {
                return accumulated to false
            }
            start += PAGE_LIMIT
            remaining--
        }
        return accumulated to true
    }

    /**
     * 根据媒体类型过滤文件（目录始终保留）。
     */
    fun filterFiles(files: List<FileInfo>?, mediaType: Int): List<FileInfo> {
        if (files.isNullOrEmpty()) return emptyList()
        return files.filter { file ->
            if (file.isDirectory()) return@filter true
            when (mediaType) {
                MediaType.IMAGE.code -> file.isImage()
                MediaType.VIDEO.code -> file.isVideo()
                MediaType.AUDIO.code -> file.isAudio()
                else -> file.isImage() || file.isVideo() // ALL(3) 及默认
            }
        }
    }

    /**
     * 批量获取文件缩略图（filemetas?thumb=1）。
     *
     * 百度 API 的 filelist 接口只为图片返回 thumbs，视频需通过 filemetas?thumb=1 获取。
     * 每次最多 100 个 fsid，超出分批请求。
     *
     * @return fsId → 缩略图 URL（跳过 /file/ 下载链接）
     */
    open suspend fun fetchThumbnails(accessToken: String, fsIds: List<Long>): Map<Long, String> {
        if (fsIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, String>()
        fsIds.distinct().chunked(100).forEach { batch ->
            val fsids = batch.joinToString(",", "[", "]")
            try {
                val resp = apiService.getFileInfoWithThumbs("filemetas", fsids, 1, accessToken)
                if (resp.isSuccess()) {
                    resp.list?.forEach { file ->
                        val thumbUrl = sequenceOf(
                            file.thumbs?.url1, file.thumbs?.url2, file.thumbs?.url3, file.thumbs?.icon,
                        ).firstOrNull { url -> !url.isNullOrBlank() && !url.contains("/file/") }
                        if (thumbUrl != null) result[file.fsId] = thumbUrl
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "批量获取缩略图失败: ${e.message}")
            }
        }
        return result
    }

    /** 暴露给外部用于判断响应是否成功（测试可见）。 */
    fun isSuccess(response: FileListResponse): Boolean = response.isSuccess()
}
