package com.baidu.tv.player.kt.repository

import com.baidu.tv.player.kt.auth.BaiduAuthService
import com.baidu.tv.player.kt.model.FileInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缩略图数据边界（Phase 10.5）。
 *
 * 将快速选播列表的缩略图获取从 [com.baidu.tv.player.kt.ui.playback.PlaybackActivity]
 * 抽出到可测试的独立边界。Activity / Controller 只依赖此接口，不再直接调用
 * [FileRepository.fetchThumbnails] 和 [BaiduAuthService.getAccessToken]。
 *
 * 语义：
 * 1. 取 access_token，为空返回空 Map。
 * 2. 过滤 fsId > 0 的文件，为空返回空 Map。
 * 3. 调用 [FileRepository.fetchThumbnails] 批量获取 fsId → 缩略图 URL。
 *
 * 不做缓存（调用方按需刷新），[kotlinx.coroutines.CancellationException] 自然传播。
 */
interface ThumbnailProvider {

    /** 批量获取缩略图 URL。返回 fsId → 已解析的缩略图 URL。 */
    suspend fun fetchThumbnails(files: List<FileInfo>): Map<Long, String>
}

/** 生产实现：委托 [FileRepository.fetchThumbnails] 与 [BaiduAuthService.getAccessToken]。 */
@Singleton
class FileRepositoryThumbnailProvider @Inject constructor(
    private val authService: BaiduAuthService,
    private val fileRepository: FileRepository,
) : ThumbnailProvider {

    override suspend fun fetchThumbnails(files: List<FileInfo>): Map<Long, String> {
        val token = authService.getAccessToken() ?: return emptyMap()
        val fsIds = files.map { it.fsId }.filter { it > 0 }
        if (fsIds.isEmpty()) return emptyMap()
        return fileRepository.fetchThumbnails(token, fsIds)
    }
}
