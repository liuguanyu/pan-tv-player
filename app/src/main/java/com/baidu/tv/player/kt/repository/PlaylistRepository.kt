package com.baidu.tv.player.kt.repository

import android.util.Log
import com.baidu.tv.player.kt.database.PlaylistDao
import com.baidu.tv.player.kt.database.PlaylistItemDao
import com.baidu.tv.player.kt.model.FileInfo
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.model.PlaylistItem
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton


private const val TAG = "PlaylistRepository"

/**
 * 事务执行器抽象：默认实现委托给 [AppDatabase.withTransaction]，
 * 便于在单测中注入同步执行版本，避免依赖真实 Room 事务调度器。
 */
fun interface TransactionRunner {
    suspend fun runInTransaction(block: suspend () -> Unit)
}

/**
 * 播放列表数据仓库。
 *
 * 全部为 `suspend` 函数，替代 Java 版的 ExecutorService + Handler + CountDownLatch。
 * - 可观察查询返回 Flow（见 DAO）。
 * - [refreshPlaylist] 用协程 + [Mutex] 保护并发刷新。
 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistItemDao: PlaylistItemDao,
    private val fileRepository: FileRepository,
    private val transactionRunner: TransactionRunner,
) {
    private val refreshMutex = Mutex()

    /** 获取所有播放列表（热流）。 */
    fun getAllPlaylists() = playlistDao.getAllPlaylists()

    /** 同步获取所有播放列表。 */
    suspend fun getAllPlaylistsSync(): List<Playlist> = playlistDao.getAllPlaylistsSync()

    /** 根据 ID 获取播放列表（热流）。 */
    fun getPlaylistById(id: Long) = playlistDao.getPlaylistById(id)

    /** 同步根据 ID 获取播放列表。 */
    suspend fun getPlaylistByIdSync(id: Long): Playlist? = playlistDao.getPlaylistByIdSync(id)

    /** 插入播放列表，返回新 id。 */
    suspend fun insertPlaylist(playlist: Playlist): Long {
        val id = playlistDao.insert(playlist)
        Log.d(TAG, "播放列表插入成功, ID: $id")
        return id
    }

    /** 更新播放列表。 */
    suspend fun updatePlaylist(playlist: Playlist) {
        playlistDao.update(playlist)
        Log.d(TAG, "播放列表更新成功, ID: ${playlist.id}")
    }

    /** 删除播放列表。 */
    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.delete(playlist)
        Log.d(TAG, "播放列表删除成功, ID: ${playlist.id}")
    }

    /** 获取播放列表项（热流）。 */
    fun getPlaylistItems(playlistId: Long) = playlistItemDao.getItemsByPlaylistId(playlistId)

    /** 同步获取播放列表项。 */
    suspend fun getPlaylistItemsSync(playlistId: Long): List<PlaylistItem> =
        playlistItemDao.getItemsByPlaylistIdSync(playlistId)

    /** 批量插入播放列表项。 */
    suspend fun insertPlaylistItems(items: List<PlaylistItem>) {
        playlistItemDao.insertAll(items)
        Log.d(TAG, "播放列表项插入成功, 数量: ${items.size}")
    }

    /**
     * 原子创建播放列表及其项目。
     *
     * 先插入 [Playlist] 取得 Room 自增 id，再把外部准备好的 [PlaylistItem] 复制为同一 playlistId
     * 后批量插入，避免 UI 层拆分两次写入导致半成品播放列表。
     */
    suspend fun createPlaylistWithItems(playlist: Playlist, items: List<PlaylistItem>): Long {
        var playlistId = 0L
        transactionRunner.runInTransaction {
            playlistId = playlistDao.insert(playlist)
            val itemsWithPlaylistId = items.map { it.copy(playlistId = playlistId) }
            playlistItemDao.insertAll(itemsWithPlaylistId)
            playlistDao.update(playlist.copy(id = playlistId, totalItems = itemsWithPlaylistId.size))
        }
        Log.d(TAG, "播放列表及项目创建成功, ID: $playlistId, 数量: ${items.size}")
        return playlistId
    }

    /** 删除播放列表的所有项。 */
    suspend fun deletePlaylistItems(playlistId: Long) {
        playlistItemDao.deleteByPlaylistId(playlistId)
        Log.d(TAG, "播放列表项删除成功, playlistId: $playlistId")
    }

    /** 获取播放列表项数量。 */
    suspend fun getPlaylistItemCount(playlistId: Long): Int =
        playlistItemDao.getItemCount(playlistId)

    /**
     * 刷新播放列表：递归重新扫描源目录 → 过滤 → 事务删除旧项并插入新项 → 更新统计。
     *
     * 用 [refreshMutex] 保护，避免同一播放列表并发刷新产生脏数据。
     * 单个源目录失败不影响其他目录（与 Java 版一致）。
     *
     * @return 新的播放列表项数量；无 token / 无源目录 / 失败时抛出 [IllegalStateException]。
     */
    suspend fun refreshPlaylist(playlist: Playlist, accessToken: String): Int = refreshMutex.withLock {
        Log.d(TAG, "开始刷新播放列表: ${playlist.name}")

        if (accessToken.isEmpty()) {
            throw IllegalStateException("未获取到访问令牌，请先登录")
        }

        val sourcePathsJson = playlist.sourcePaths
        if (sourcePathsJson.isNullOrEmpty()) {
            throw IllegalStateException("播放列表没有源目录信息，无法刷新")
        }

        val sourcePaths: List<String> = try {
            Gson().fromJson(sourcePathsJson, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "解析源目录路径失败", e)
            throw IllegalStateException("解析源目录路径失败", e)
        }
        if (sourcePaths.isEmpty()) {
            throw IllegalStateException("播放列表源目录为空，无法刷新")
        }

        // 递归获取各源目录文件（单目录失败不影响整体）。
        // refreshMutex 已保证同一播放列表不会并发刷新，此处顺序收集即可。
        val allFiles = mutableListOf<FileInfo>()
        var successfulSources = 0
        val failedSources = mutableListOf<String>()
        for (path in sourcePaths) {
            try {
                allFiles.addAll(fileRepository.fetchFilesRecursive(accessToken, path))
                successfulSources++
            } catch (e: Exception) {
                Log.e(TAG, "获取目录文件失败: $path, error: ${e.message}")
                failedSources += path
            }
        }
        // 刷新采用全量快照语义：任一源目录失败都不提交，避免把失败目录中的旧文件误删。
        if (failedSources.isNotEmpty()) {
            throw IllegalStateException("${failedSources.size}个网盘源目录刷新失败，已保留原播放列表")
        }

        Log.d(TAG, "获取到新文件列表，总数: ${allFiles.size}, 成功目录数: $successfulSources")

        // 过滤并转换为播放列表项
        val targetMediaType = playlist.mediaType
        val newItems = ArrayList<PlaylistItem>()
        // 多个源目录可能重叠，按 fs_id（缺失时按 path）去重，避免同一文件重复进入列表。
        val uniqueFiles = allFiles.distinctBy { file ->
            if (file.fsId > 0L) "id:${file.fsId}" else "path:${file.path.orEmpty()}"
        }
        for (fileInfo in uniqueFiles) {
            val shouldAdd = when (targetMediaType) {
                0 -> fileInfo.isVideo() || fileInfo.isImage() // 混合
                1 -> fileInfo.isVideo()
                2 -> fileInfo.isImage()
                else -> fileInfo.isVideo() || fileInfo.isImage()
            }
            if (!shouldAdd) continue

            val itemMediaType = if (fileInfo.isVideo()) 1 else 2
            newItems += PlaylistItem(
                playlistId = playlist.id,
                fsId = fileInfo.fsId,
                filePath = fileInfo.path,
                fileName = fileInfo.serverFilename,
                mediaType = itemMediaType,
                sortOrder = newItems.size,
                duration = 0, // 列表接口不返回时长，播放时再取
                fileSize = fileInfo.size,
            )
        }

        // 刷新后尽量保持当前播放文件；文件已删除时再把索引约束到新列表范围。
        val oldItems = playlistItemDao.getItemsByPlaylistIdSync(playlist.id)
        val currentFsId = oldItems.getOrNull(playlist.lastPlayedIndex)?.fsId
        val refreshedIndex = currentFsId
            ?.let { fsId -> newItems.indexOfFirst { it.fsId == fsId }.takeIf { it >= 0 } }
            ?: playlist.lastPlayedIndex.coerceIn(0, maxOf(0, newItems.lastIndex))

        // 事务：删旧项 → 插新项 → 更新统计和播放位置。成功扫描到空目录时允许同步为空。
        transactionRunner.runInTransaction {
            playlistItemDao.deleteByPlaylistId(playlist.id)
            playlistItemDao.insertAll(newItems)
            playlist.totalItems = newItems.size
            playlist.lastPlayedIndex = refreshedIndex
            playlistDao.update(playlist)
        }

        Log.d(TAG, "播放列表刷新完成，新文件数: ${newItems.size}")
        newItems.size
    }
}
