package com.baidu.tv.player.kt.repository

import com.baidu.tv.player.kt.database.PlaybackHistoryDao
import com.baidu.tv.player.kt.model.PlaybackHistory
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 播放历史记录数据仓库（文件级）。
 *
 * 全部为 `suspend` 函数，替代 Java 版 ExecutorService。
 * [insert] 对同一文件（folderPath 存文件路径，作为去重键）做 upsert：
 * 已存在则把该记录移动到列表队首（更新 lastPlayTime 为最新，并刷新封面/来源上下文），
 * 不存在则插入；随后按先进先出（FIFO）裁剪到 [MAX_HISTORY] 条。
 */
@Singleton
class PlaybackHistoryRepository @Inject constructor(
    private val historyDao: PlaybackHistoryDao,
    private val transactionRunner: TransactionRunner,
) {
    /** 获取所有历史记录（热流）。 */
    fun getAllHistory(): Flow<List<PlaybackHistory>> = historyDao.getAllHistory()

    /** 获取最近的历史记录。 */
    fun getRecentHistory(limit: Int): Flow<List<PlaybackHistory>> = historyDao.getRecentHistory(limit)

    /** 获取前 4 条历史（主界面最近任务）。 */
    fun getTop4History(): Flow<List<PlaybackHistory>> = historyDao.getTop4History()

    /** 根据 ID 获取历史记录。 */
    fun getHistoryById(id: Long): Flow<PlaybackHistory?> = historyDao.getHistoryById(id)

    /**
     * 插入/更新文件级历史（upsert）：
     * - 相同文件（folderPath）已存在：更新为最新时间并刷新封面/来源，等价移动到列表队首；
     * - 不存在：插入新记录；
     * - 无论哪种情况，写入后按 FIFO 裁剪到 [MAX_HISTORY] 条。
     */
    suspend fun insert(history: PlaybackHistory) {
        transactionRunner.runInTransaction {
            val now = System.currentTimeMillis()
            val existing = historyDao.getHistoryByPath(history.folderPath)
            if (existing != null) {
                historyDao.update(
                    existing.copy(
                        folderName = history.folderName ?: existing.folderName,
                        mediaType = history.mediaType,
                        fileCount = history.fileCount,
                        lastPlayTime = now,
                        coverImagePath = history.coverImagePath ?: existing.coverImagePath,
                        sourcePlaylistId = history.sourcePlaylistId,
                        sourceFolderPath = history.sourceFolderPath,
                        fsId = history.fsId,
                    ),
                )
            } else {
                historyDao.insert(history.copy(lastPlayTime = now))
            }
            // upsert 与容量裁剪保持原子性，避免其他写入在两步之间插入导致超限。
            if (historyDao.count() > MAX_HISTORY) {
                historyDao.trimToLimit(MAX_HISTORY)
            }
        }
    }

    /** 更新已经播放成功的本地封面，避免首页再次请求远程缩略图。 */
    suspend fun updateCover(filePath: String, coverPath: String) {
        historyDao.updateCoverPath(filePath, coverPath)
    }

    /** 删除历史记录。 */
    suspend fun delete(history: PlaybackHistory) {
        historyDao.delete(history)
    }

    /** 删除所有历史记录。 */
    suspend fun deleteAll() {
        historyDao.deleteAll()
    }

    companion object {
        /** 最近播放最大容量，超出按先进先出淘汰。 */
        const val MAX_HISTORY = 100
    }
}
