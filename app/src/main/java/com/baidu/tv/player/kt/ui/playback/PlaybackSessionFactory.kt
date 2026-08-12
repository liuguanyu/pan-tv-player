package com.baidu.tv.player.kt.ui.playback

import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.model.PlaylistItem
import com.baidu.tv.player.kt.repository.PlaybackHistoryRepository
import com.baidu.tv.player.kt.repository.PlaylistRepository
import com.baidu.tv.player.kt.repository.SettingsRepository
import com.baidu.tv.player.kt.util.PlaylistCache
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放会话工厂（Phase 7）。
 *
 * 将 [PlaybackViewModel] 的三条会话构建路径统一为三个工厂方法，
 * 消除 ViewModel 对 Room 实体（[PlaybackHistory]、[PlaylistItem]）的直接依赖。
 *
 * 语义约束（不可回归）：
 * - 最近播放会话从**同一 Room 快照**定位点击项：只查询一次 [PlaybackHistoryRepository.getRecentHistory]，
 *   贯积 [PlaybackHistoryRepository.getHistoryById] 结果后一次性构建，不因数据库热流重排。
 * - `fsId <= 0` 的历史记录从快照中过滤。
 * - 空历史 → 抛出 [PlaybackSessionException]，不静默回退。
 * - 目标不在快照中 → 抛出 [PlaybackSessionException]，不回退到其他索引。
 * - 返回的 [PlaybackSession.items] 是不可变快照（[List] 拷贝）。
 */
@Singleton
class PlaybackSessionFactory @Inject constructor(
    private val playlistCache: PlaylistCache,
    private val playlistRepository: PlaylistRepository,
    private val historyRepository: PlaybackHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val queueNavigator: PlaybackQueueNavigator,
) {

    // ------------------------------------------------------------------
    // 目录缓存
    // ------------------------------------------------------------------

    /**
     * 从 [PlaylistCache] 构建会话。
     *
     * @param playlistId   缓存键（由文件浏览页写入）。
     * @param mediaType    媒体类型 code。
     * @param folderPath   云盘目录路径（同时用于展示和来源记录）。
     * @param startIndex   调用方首选起始索引。
     * @param selectedPath 可选：用户选中的文件路径，优先于 [startIndex] 定位。
     * @throws PlaybackSessionException 缓存未命中或列表为空。
     */
    fun fromDirectoryCache(
        playlistId: String,
        mediaType: Int,
        folderPath: String,
        startIndex: Int,
        selectedPath: String?,
    ): PlaybackSession {
        val files = playlistCache.getAndRemove(playlistId)?.toList()
        if (files.isNullOrEmpty()) {
            throw PlaybackSessionException("播放列表为空")
        }
        val selectedIndex = selectedPath
            ?.let { path -> files.indexOfFirst { it.path == path }.takeIf { it >= 0 } }
        val safeIndex = selectedIndex ?: resolveStartIndex(files.size, startIndex)
        val folderName = folderPath.trimEnd('/').substringAfterLast('/').ifEmpty { folderPath }
        return PlaybackSession(
            source = PlaybackSource.DIRECTORY_CACHE,
            playlistId = playlistId,
            items = files,
            folderPath = folderPath,
            title = folderName,
            startIndex = safeIndex,
            mediaType = mediaType,
            sourcePlaylistId = null,
            sourceFolderPath = folderPath,
        )
    }

    // ------------------------------------------------------------------
    // 数据库播放列表
    // ------------------------------------------------------------------

    /**
     * 从 Room 数据库播放列表构建会话。
     *
     * @param playlistDbId 播放列表主键。
     * @param startFsId    可选：从指定 fs_id 的文件开始播放（点击"最近播放"进入时定位到该文件）。
     * @throws PlaybackSessionException 播放列表不存在或为空。
     */
    suspend fun fromDatabasePlaylist(
        playlistDbId: Long,
        startFsId: Long?,
    ): PlaybackSession {
        val playlist = playlistRepository.getPlaylistByIdSync(playlistDbId)
            ?: throw PlaybackSessionException("播放列表不存在")
        val files = playlistRepository.getPlaylistItemsSync(playlistDbId)
            .map { it.toFileInfo() }
        if (files.isEmpty()) {
            throw PlaybackSessionException("播放列表为空")
        }
        val mediaTypeCode = playlist.mediaType.toPlaybackMediaTypeCode()
        val safeIndex = startFsId
            ?.let { fid -> files.indexOfFirst { it.fsId == fid }.takeIf { it >= 0 } }
            ?: resolveStartIndex(files.size, playlist.lastPlayedIndex)
        return PlaybackSession(
            source = PlaybackSource.DATABASE_PLAYLIST,
            playlistId = playlistDbId.toString(),
            items = files,
            folderPath = playlist.name,
            title = playlist.name,
            startIndex = safeIndex,
            mediaType = mediaTypeCode,
            sourcePlaylistId = playlistDbId,
            sourceFolderPath = null,
        )
    }

    // ------------------------------------------------------------------
    // 最近播放历史
    // ------------------------------------------------------------------

    /**
     * 从最近播放历史构建会话。
     *
     * 从同一 Room 快照（[PlaybackHistoryRepository.getRecentHistory] 一次查询）定位被点击的文件，
     * 创建后数据库热流变化不重排当前会话。
     *
     * @param historyId 被点击的历史记录主键。
     * @throws PlaybackSessionException 历史为空、被点击记录不存在或其 fsId 不在快照中。
     */
    suspend fun fromRecentHistory(historyId: Long): PlaybackSession {
        val allHistory = historyRepository
            .getRecentHistory(PlaybackHistoryRepository.MAX_HISTORY)
            .first()
            .filter { it.fsId > 0L }
        if (allHistory.isEmpty()) {
            throw PlaybackSessionException("播放历史为空")
        }
        val files = allHistory.map { it.toPlaybackFileInfo() }
        val clickedHistory = historyRepository.getHistoryById(historyId).first()
        val safeIndex = if (clickedHistory != null && clickedHistory.fsId > 0L) {
            files.indexOfFirst { it.fsId == clickedHistory.fsId }.takeIf { it >= 0 }
        } else {
            null
        }
        if (safeIndex == null) {
            throw PlaybackSessionException("该记录不在最近播放中")
        }
        return PlaybackSession(
            source = PlaybackSource.RECENT_HISTORY,
            playlistId = "history-$historyId",
            items = files,
            folderPath = "最近播放",
            title = "最近播放",
            startIndex = safeIndex,
            mediaType = MediaType.ALL.code,
            sourcePlaylistId = null,
            sourceFolderPath = null,
        )
    }

    // ------------------------------------------------------------------
    // 起始索引解析（与旧 ViewModel 行为一致）
    // ------------------------------------------------------------------

    /**
     * 根据当前播放模式解析起始索引。
     *
     * 直接读取 [SettingsRepository.playMode] 的 StateFlow 当前值，
     * 避免依赖尚未 collect 完成的 uiState.playMode。
     */
    private fun resolveStartIndex(size: Int, preferredIndex: Int): Int =
        queueNavigator.initialIndex(size, preferredIndex, settingsRepository.playMode.value)

    // ------------------------------------------------------------------
    // Room 实体 → FileInfo 映射（从 ViewModel 迁移）
    // ------------------------------------------------------------------

    /** PlaylistItem.mediaType: 1=视频, 2=图片 → FileInfo.category: 1=视频, 3=图片。 */
    private fun PlaylistItem.toFileInfo(): FileInfo = FileInfo(
        fsId = fsId,
        path = filePath,
        serverFilename = fileName,
        size = fileSize,
        category = if (mediaType == 1) 1 else 3,
    )

    /** PlaybackHistory → FileInfo（最近播放列表项映射）。 */
    private fun PlaybackHistory.toPlaybackFileInfo(): FileInfo = FileInfo(
        fsId = fsId,
        path = folderPath,
        serverFilename = folderName,
        size = 0,
        category = when (mediaType) {
            MediaType.VIDEO.code -> 1
            else -> 3
        },
    )

    /** Playlist.mediaType(0=混合,1=视频,2=图片) → MediaType code(1=图片,2=视频,3=混合)。 */
    private fun Int.toPlaybackMediaTypeCode(): Int = when (this) {
        1 -> MediaType.VIDEO.code
        2 -> MediaType.IMAGE.code
        else -> MediaType.ALL.code
    }
}
