package com.baidu.tv.player.kt.repository

import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.network.BaiduPanService

/**
 * 可注入的 Fake [FileRepository]，用于 ViewModel 单测隔离网络与分页逻辑。
 * 预设各方法的返回值；默认返回空列表。
 */
open class FakeFileRepository(
    apiService: BaiduPanService = com.baidu.tv.player.kt.network.FakeBaiduPanService(),
) : FileRepository(apiService) {

    var fileListResult: List<FileInfo> = emptyList()
    var nonRecursiveResult: List<FileInfo> = emptyList()
    var recursiveResult: List<FileInfo> = emptyList()
    var fileDetailResult: FileInfo? = null
    var fileDetailFsId: Long? = null

    var getFileListCalls = mutableListOf<Triple<String, String, Int>>()
    var recursiveCalls = mutableListOf<String>()

    override suspend fun getFileList(
        accessToken: String, dirPath: String, mediaType: Int, maxPages: Int,
    ): List<FileInfo> {
        getFileListCalls += Triple(accessToken, dirPath, mediaType)
        return fileListResult
    }

    override suspend fun fetchFilesNonRecursive(accessToken: String, dirPath: String): List<FileInfo> {
        return nonRecursiveResult
    }

    override suspend fun fetchFilesRecursive(
        accessToken: String, dirPath: String, maxDirs: Int,
    ): List<FileInfo> {
        recursiveCalls += dirPath
        return recursiveResult
    }

    override suspend fun getFileListRecursive(
        accessToken: String, dirPath: String, mediaType: Int,
    ): List<FileInfo> = recursiveResult

    override suspend fun fetchFileDetail(accessToken: String, fsId: Long): FileInfo? {
        fileDetailFsId = fsId
        return fileDetailResult
    }
}
