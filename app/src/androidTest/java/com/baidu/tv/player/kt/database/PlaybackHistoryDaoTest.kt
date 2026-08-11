package com.baidu.tv.player.kt.database

import app.cash.turbine.test
import com.baidu.tv.player.kt.model.PlaybackHistory
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

/**
 * PlaybackHistoryDao 集成测试：插入 → 按时间倒序查询 → 删除。内存数据库。
 */
@HiltAndroidTest
class PlaybackHistoryDaoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var historyDao: PlaybackHistoryDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() = runTest {
        historyDao.deleteAll()
    }

    private fun history(folder: String, lastPlayTime: Long, name: String = folder) =
        PlaybackHistory(
            folderPath = folder,
            folderName = name,
            mediaType = 3,
            fileCount = 10,
            lastPlayTime = lastPlayTime,
            createTime = 0L,
        )

    @Test
    fun insert_thenGetByPath_returnsHistory() = runTest {
        historyDao.insert(history("/A", 100L))
        val loaded = historyDao.getHistoryByPath("/A")
        assertEquals("/A", loaded?.folderPath)
    }

    @Test
    fun insert_samePath_replacesExistingRecordWithoutDuplicate() = runTest {
        historyDao.insert(history("/A", 100L, name = "old"))
        historyDao.insert(history("/A", 200L, name = "new"))

        val all = historyDao.getAllHistorySync()
        assertEquals(1, all.size)
        assertEquals("new", all.single().folderName)
    }

    @Test
    fun getAllHistorySync_orderedByLastPlayTimeDesc() = runTest {
        historyDao.insert(history("/old", 100L))
        historyDao.insert(history("/new", 999L))
        historyDao.insert(history("/mid", 500L))
        val all = historyDao.getAllHistorySync()
        assertEquals(listOf("/new", "/mid", "/old"), all.map { it.folderPath })
    }

    @Test
    fun getAllHistorySync_sameTimestampUsesNewestIdFirst() = runTest {
        historyDao.insert(history("/first", 100L))
        historyDao.insert(history("/second", 100L))

        val all = historyDao.getAllHistorySync()
        assertEquals(listOf("/second", "/first"), all.map { it.folderPath })
    }

    @Test
    fun delete_removesHistory() = runTest {
        historyDao.insert(history("/A", 100L))
        val h = historyDao.getHistoryByPath("/A")!!
        historyDao.delete(h)
        assertNull(historyDao.getHistoryByPath("/A"))
    }

    @Test
    fun getRecentHistory_limitsResultCount() = runTest {
        for (i in 1..10) historyDao.insert(history("/$i", i.toLong()))
        historyDao.getRecentHistory(4).test {
            val first = awaitItem()
            assertEquals(4, first.size)
            // 最近（lastPlayTime 最大）在前
            assertEquals("/10", first[0].folderPath)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getTop4History_returnsAtMostFour() = runTest {
        for (i in 1..6) historyDao.insert(history("/$i", i.toLong()))
        historyDao.getTop4History().test {
            val first = awaitItem()
            assertEquals(4, first.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAllHistoryFlow_emitsOnInsertAndDelete() = runTest {
        historyDao.getAllHistory().test {
            assertEquals(emptyList<PlaybackHistory>(), awaitItem())
            historyDao.insert(history("/A", 100L))
            assertEquals(1, awaitItem().size)
            val all = historyDao.getAllHistorySync()
            historyDao.delete(all.first())
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
