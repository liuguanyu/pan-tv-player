package com.baidu.tv.player.kt.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.model.PlaylistItem

/**
 * Room 数据库。KSP 生成实现。
 *
 * - 实例由 Hilt [com.baidu.tv.player.kt.di.DatabaseModule] 提供，不使用手动单例。
 * - 不再使用 `fallbackToDestructiveMigration()`：后续 schema 变更须提供显式 Migration。
 * - version=3：playback_history 增加 coverImagePath / sourcePlaylistId 两列（[MIGRATION_2_3]）。
 * - version=4：playback_history 改为**文件级**语义，增加 sourceFolderPath / fsId 两列，
 *   并清空旧的目录级历史数据（旧数据语义不兼容，见 [MIGRATION_3_4]）。
 * - version=5：清理相同文件路径的重复历史，并为 folderPath 增加唯一索引（[MIGRATION_4_5]）。
 */
@Database(
    entities = [PlaybackHistory::class, Playlist::class, PlaylistItem::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao

    companion object {
        const val DATABASE_NAME = "baidu_tv_player.db"

        /**
         * v2 -> v3：为 playback_history 增加两列，支持"最近播放"卡片展示封面、
         * 并区分播放列表来源（点击时按列表重播而非按目录拉取，避免 API 报错）。
         * 两列均可空，新增列不影响既有数据。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_history ADD COLUMN coverImagePath TEXT")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN sourcePlaylistId INTEGER")
            }
        }

        /**
         * v3 -> v4：playback_history 由目录级改为文件级。
         * - 新增 sourceFolderPath（来源云盘目录，可空）与 fsId（文件 fs_id，用于定位起始播放项）。
         * - 旧记录的 folderPath 存的是目录/列表名，语义与文件级不兼容，点击会导致 API 报错，
         *   故清空旧数据，让用户重新播放后按文件级重新累积。
         */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playback_history ADD COLUMN sourceFolderPath TEXT")
                db.execSQL("ALTER TABLE playback_history ADD COLUMN fsId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("DELETE FROM playback_history")
            }
        }

        /**
         * v4 -> v5：folderPath 是文件级最近播放的业务唯一键。
         * 先删除重复路径的旧记录（lastPlayTime 相同时保留 id 最大者），再建立唯一索引，
         * 防止并发写入产生同一文件的多条历史。
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "DELETE FROM playback_history WHERE EXISTS (" +
                        "SELECT 1 FROM playback_history newer " +
                        "WHERE newer.folderPath = playback_history.folderPath " +
                        "AND (newer.lastPlayTime > playback_history.lastPlayTime " +
                        "OR (newer.lastPlayTime = playback_history.lastPlayTime " +
                        "AND newer.id > playback_history.id)))",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_playback_history_folderPath ON playback_history(folderPath)",
                )
            }
        }
    }
}
