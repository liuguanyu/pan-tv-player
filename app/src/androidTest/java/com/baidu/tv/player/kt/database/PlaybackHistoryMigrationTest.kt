package com.baidu.tv.player.kt.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Room v4 → v5 Migration 测试（Tasks 3.2–3.4）。
 *
 * 验证 [AppDatabase.MIGRATION_4_5] 的去重逻辑、唯一索引创建和 schema validation。
 *
 * v4 schema: playback_history 有 folderPath 列但无唯一索引。
 * v5 schema: folderPath 有唯一索引；迁移时先去重（保留 lastPlayTime 最大 / id 最大者）。
 */
@RunWith(AndroidJUnit4::class)
class PlaybackHistoryMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = AppDatabase::class.java,
        specs = emptyList(),
    )

    private val dbName = "migration_test.db"

    // ----------------------------------------------------------------
    // Task 3.2: 构造包含重复路径、相同时间和非重复记录的 v4 数据库
    // ----------------------------------------------------------------

    @Test
    fun migrate4To5_deduplicatesByFolderPath_keepsNewestLastPlayTime() {
        migrationHelper.createDatabase(dbName, 4).use { db ->
            insertV4History(db, folderPath = "/A/a.mp4", folderName = "a.mp4", lastPlayTime = 100L)
            insertV4History(db, folderPath = "/A/a.mp4", folderName = "a-old.mp4", lastPlayTime = 300L)
            insertV4History(db, folderPath = "/A/a.mp4", folderName = "a-newest.mp4", lastPlayTime = 500L)
            insertV4History(db, folderPath = "/B/b.mp4", folderName = "b.mp4", lastPlayTime = 200L)
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.MIGRATION_4_5,
        )
        migratedDb.use {
            val rows = selectAll(it)
            assertEquals(2, rows.size)
            // /A/a.mp4 保留 lastPlayTime=500 的记录
            assertEquals("/A/a.mp4", rows[0]["folderPath"])
            assertEquals("a-newest.mp4", rows[0]["folderName"])
            assertEquals(500L, rows[0]["lastPlayTime"])
            // /B/b.mp4 保留
            assertEquals("/B/b.mp4", rows[1]["folderPath"])
        }
    }

    // ----------------------------------------------------------------
    // Task 3.3: 相同时间保留 id 最大者；唯一索引通过 schema validation
    // ----------------------------------------------------------------

    @Test
    fun migrate4To5_sameLastPlayTime_keepsHighestId() {
        migrationHelper.createDatabase(dbName, 4).use { db ->
            insertV4History(db, folderPath = "/A/a.mp4", folderName = "first", lastPlayTime = 100L)
            insertV4History(db, folderPath = "/A/a.mp4", folderName = "second", lastPlayTime = 100L)
            insertV4History(db, folderPath = "/A/a.mp4", folderName = "third", lastPlayTime = 100L)
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.MIGRATION_4_5,
        )
        migratedDb.use {
            val rows = selectAll(it)
            assertEquals(1, rows.size)
            // id 最大者 = 第三条（"third"）
            assertEquals("third", rows[0]["folderName"])
        }
    }

    @Test
    fun migrate4To5_preservesNonDuplicateRecords() {
        migrationHelper.createDatabase(dbName, 4).use { db ->
            insertV4History(db, folderPath = "/A/a.mp4", folderName = "a.mp4", lastPlayTime = 100L)
            insertV4History(db, folderPath = "/B/b.mp4", folderName = "b.mp4", lastPlayTime = 200L)
            insertV4History(db, folderPath = "/C/c.mp4", folderName = "c.mp4", lastPlayTime = 300L)
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.MIGRATION_4_5,
        )
        migratedDb.use {
            val rows = selectAll(it)
            assertEquals(3, rows.size)
            assertEquals("/C/c.mp4", rows[0]["folderPath"])
            assertEquals("/B/b.mp4", rows[1]["folderPath"])
            assertEquals("/A/a.mp4", rows[2]["folderPath"])
        }
    }

    @Test
    fun migrate4To5_emptyDatabase_succeedsAndCreatesUniqueIndex() {
        migrationHelper.createDatabase(dbName, 4).use { }
        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.MIGRATION_4_5,
        )
        migratedDb.use {
            val rows = selectAll(it)
            assertEquals(0, rows.size)
        }
    }

    // ----------------------------------------------------------------
    // Task 3.4: 中文/空格/引号路径和迁移后重复插入
    // ----------------------------------------------------------------

    @Test
    fun migrate4To5_chinesePath_deduplicatesCorrectly() {
        migrationHelper.createDatabase(dbName, 4).use { db ->
            insertV4History(db, folderPath = "/电影/视频.mp4", folderName = "视频.mp4", lastPlayTime = 100L)
            insertV4History(db, folderPath = "/电影/视频.mp4", folderName = "视频-new.mp4", lastPlayTime = 200L)
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.MIGRATION_4_5,
        )
        migratedDb.use {
            val rows = selectAll(it)
            assertEquals(1, rows.size)
            assertEquals("/电影/视频.mp4", rows[0]["folderPath"])
            assertEquals("视频-new.mp4", rows[0]["folderName"])
        }
    }

    @Test
    fun migrate4To5_pathWithSpaces_deduplicatesCorrectly() {
        migrationHelper.createDatabase(dbName, 4).use { db ->
            insertV4History(db, folderPath = "/my videos/file 1.mp4", folderName = "file 1.mp4", lastPlayTime = 100L)
            insertV4History(db, folderPath = "/my videos/file 1.mp4", folderName = "file 1 new.mp4", lastPlayTime = 200L)
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.MIGRATION_4_5,
        )
        migratedDb.use {
            val rows = selectAll(it)
            assertEquals(1, rows.size)
            assertEquals("/my videos/file 1.mp4", rows[0]["folderPath"])
        }
    }

    @Test
    fun migrate4To5_pathWithQuotes_deduplicatesCorrectly() {
        migrationHelper.createDatabase(dbName, 4).use { db ->
            insertV4History(db, folderPath = "/quotes/\"file\".mp4", folderName = "\"file\".mp4", lastPlayTime = 100L)
            insertV4History(db, folderPath = "/quotes/\"file\".mp4", folderName = "\"file\"-new.mp4", lastPlayTime = 200L)
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.MIGRATION_4_5,
        )
        migratedDb.use {
            val rows = selectAll(it)
            assertEquals(1, rows.size)
            assertEquals("/quotes/\"file\".mp4", rows[0]["folderPath"])
        }
    }

    @Test
    fun postMigration_duplicateInsertFailsDueToUniqueIndex() {
        migrationHelper.createDatabase(dbName, 4).use { db ->
            insertV4History(db, folderPath = "/A/a.mp4", folderName = "a.mp4", lastPlayTime = 100L)
        }

        val migratedDb = migrationHelper.runMigrationsAndValidate(
            dbName, 5, true, AppDatabase.MIGRATION_4_5,
        )
        migratedDb.use {
            try {
                it.execSQL(
                    "INSERT INTO playback_history (folderPath, folderName, mediaType, fileCount, " +
                        "lastPlayTime, createTime, sourceFolderPath, fsId) " +
                        "VALUES ('/A/a.mp4', 'dup', 1, 1, 999, 0, null, 0)",
                )
                assert(false) { "Expected UNIQUE constraint violation" }
            } catch (e: Exception) {
                assertTrue(
                    "Expected UNIQUE constraint violation, got: ${e.message}",
                    e.message?.contains("UNIQUE", ignoreCase = true) == true,
                )
            }
        }
    }

    // ----------------------------------------------------------------
    // 辅助方法
    // ----------------------------------------------------------------

    private fun insertV4History(
        db: SupportSQLiteDatabase,
        folderPath: String,
        folderName: String,
        lastPlayTime: Long,
    ) {
        db.execSQL(
            "INSERT INTO playback_history (folderPath, folderName, mediaType, fileCount, " +
                "lastPlayTime, createTime, sourceFolderPath, fsId) " +
                "VALUES (?, ?, 1, 1, ?, 0, null, 0)",
            arrayOf(folderPath, folderName, lastPlayTime),
        )
    }

    /** 查询所有行，按 lastPlayTime DESC 排序，返回列名→值的 Map 列表。 */
    private fun selectAll(db: SupportSQLiteDatabase): List<Map<String, Any?>> {
        val result = mutableListOf<Map<String, Any?>>()
        db.query("SELECT * FROM playback_history ORDER BY lastPlayTime DESC").use { cursor ->
            val columnNames = cursor.columnNames
            while (cursor.moveToNext()) {
                val row = mutableMapOf<String, Any?>()
                for (i in columnNames.indices) {
                    row[columnNames[i]] = when (cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                        android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                        android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                        else -> null
                    }
                }
                result.add(row)
            }
        }
        return result
    }
}
