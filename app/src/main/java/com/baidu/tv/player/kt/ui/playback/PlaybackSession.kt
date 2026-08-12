package com.baidu.tv.player.kt.ui.playback

import com.baidu.tv.player.kt.model.FileInfo

/**
 * 播放会话来源（Phase 7）。
 *
 * 标识 [PlaybackSession] 的数据来源，便于日志、调试和未来扩展。
 */
enum class PlaybackSource {
    /** 来自 [com.baidu.tv.player.kt.util.PlaylistCache] 的目录缓存。 */
    DIRECTORY_CACHE,
    /** 来自 Room 数据库播放列表。 */
    DATABASE_PLAYLIST,
    /** 来自最近播放历史。 */
    RECENT_HISTORY,
}

/**
 * 不可变播放会话（Phase 7）。
 *
 * 将目录缓存、数据库播放列表和最近播放三种数据来源统一转换为同一类型，
 * 使 [PlaybackViewModel] 不再直接依赖 Room 实体或 PlaylistItem 映射逻辑。
 *
 * 不变量：
 * - [items] 在创建后不可重排或修改；调用方应使用 [toList] 快照。
 * - [startIndex] 始终在 `0..items.lastIndex` 范围内（空列表时为 0）。
 * - 最近播放会话的 [items] 来自同一 Room 快照，创建后不受数据库热流影响。
 *
 * @param source    数据来源标识。
 * @param playlistId UiState.playlistId 对应的字符串标识。
 * @param items     不可变播放列表项。
 * @param folderPath UiState.folderPath 对应的展示路径。
 * @param title     UiState.folderName 对应的展示名称。
 * @param startIndex 起始播放索引（已解析，含播放模式决策）。
 * @param mediaType  媒体类型 code（与 [com.baidu.tv.player.kt.model.MediaType] 一致）。
 * @param sourcePlaylistId 来源数据库播放列表 id（可空）。
 * @param sourceFolderPath  来源云盘目录路径（可空）。
 */
data class PlaybackSession(
    val source: PlaybackSource,
    val playlistId: String,
    val items: List<FileInfo>,
    val folderPath: String,
    val title: String,
    val startIndex: Int,
    val mediaType: Int,
    val sourcePlaylistId: Long?,
    val sourceFolderPath: String?,
) {
    init {
        require(startIndex >= 0) { "startIndex must be non-negative, was $startIndex" }
        require(items.isEmpty() || startIndex < items.size) {
            "startIndex $startIndex out of bounds for ${items.size} items"
        }
    }
}

/**
 * 会话构建失败异常（Phase 7）。
 *
 * 携带面向用户的中文错误消息，由 [PlaybackViewModel] 捕获后通过
 * [PlaybackUiEvent.ShowError] 发出。
 */
class PlaybackSessionException(
    message: String,
) : RuntimeException(message)
