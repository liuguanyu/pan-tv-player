package com.baidu.tv.player.kt.ui.playback

import android.util.Log
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放历史记录器（Phase 11）。
 *
 * 将 [MediaPreparationCoordinator] / [PlaybackViewModel] 中"文件级最近播放"的构建与写入降级
 * 逻辑提取为独立组件，便于单测覆盖映射规则与异常处理。
 *
 * 映射规则：
 * 1. **路径回退**：`file.path` 非空 → 直接用；否则用 `state.folderPath` + `file.serverFilename`
 *    拼接；两者都为空 → 不写库。
 * 2. **媒体类型**：视频 → [MediaType.VIDEO.code]，否则（含图片）→ [MediaType.IMAGE.code]。
 * 3. **来源上下文**：`sourcePlaylistId` / `sourceFolderPath` 直接取自 [PlaybackUiState]。
 * 4. **封面候选优先级**：`thumbs.icon` → `url1` → `url2` → `url3`，跳过 null/blank 与
 *    含 `/file/` 的 URL（百度内部缩略图占位）。`thumbs` 取自 `file.thumbs`，为空时回退到
 *    [fileDetail] 中的 thumbs（来自详情缓存）。
 *
 * 写入降级：
 * - **不使用 `runCatching`**：用 `try/catch`，[CancellationException] 重新抛出。
 * - **Room 异常不中断播放**：非取消异常捕获后仅记录日志，不影响调用方协程。
 *
 * 去重由 [PlaybackHistoryRepository.insert] 的 upsert 语义保证（以 `folderPath` 为唯一键）。
 */
@Singleton
class PlaybackHistoryRecorder @Inject constructor(
    private val historyRepository: PlaybackHistoryRepository,
) {
    /**
     * 记录当前播放文件到最近播放（文件级）。
     *
     * @param file       本次播放的文件。
     * @param state      当前 UI 状态（提供来源上下文与 folderPath）。
     * @param fileDetail 可选：来自详情缓存的完整 [FileInfo]（含 thumbs），用于封面回退。
     * @return 写入成功返回 true；因路径为空跳过或写库失败返回 false。
     */
    suspend fun record(file: FileInfo, state: PlaybackUiState, fileDetail: FileInfo? = null): Boolean {
        val history = mapToHistory(file, state, fileDetail) ?: return false
        return try {
            historyRepository.insert(history)
            true
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.e(TAG, "更新最近播放失败: ${history.folderPath}", e)
            false
        }
    }

    /**
     * 将 [file] + [state] + [fileDetail] 映射为 [PlaybackHistory]。
     *
     * 路径为空（file.path 与 serverFilename/folderPath 拼接都不可用）时返回 null，调用方应跳过写库。
     */
    internal fun mapToHistory(file: FileInfo, state: PlaybackUiState, fileDetail: FileInfo?): PlaybackHistory? {
        val filePath = resolveFilePath(file, state) ?: return null
        val fileName = file.serverFilename ?: filePath.substringAfterLast('/')
        val mediaType = if (file.isVideo()) MediaType.VIDEO.code else MediaType.IMAGE.code
        val thumbs = file.thumbs ?: fileDetail?.thumbs
        val cover = selectCover(thumbs)
        return PlaybackHistory(
            folderPath = filePath,
            folderName = fileName,
            mediaType = mediaType,
            fileCount = 1,
            createTime = System.currentTimeMillis(),
            coverImagePath = cover,
            sourcePlaylistId = state.sourcePlaylistId,
            sourceFolderPath = state.sourceFolderPath,
            fsId = file.fsId,
        )
    }

    /** 路径回退：file.path → folderPath + serverFilename → null。 */
    private fun resolveFilePath(file: FileInfo, state: PlaybackUiState): String? {
        file.path?.takeIf { it.isNotBlank() }?.let { return it }
        val name = file.serverFilename ?: return null
        val folder = state.folderPath.trimEnd('/')
        return if (folder.isNotEmpty()) "$folder/$name" else "/$name"
    }

    /** 封面候选优先级：icon → url1 → url2 → url3，跳过 null/blank 与含 `/file/` 的 URL。 */
    private fun selectCover(thumbs: FileInfo.Thumbs?): String? =
        sequenceOf(thumbs?.icon, thumbs?.url1, thumbs?.url2, thumbs?.url3)
            .firstOrNull { url -> !url.isNullOrBlank() && !url.contains("/file/") }

    private companion object {
        const val TAG = "PlaybackHistoryRecorder"
    }
}
