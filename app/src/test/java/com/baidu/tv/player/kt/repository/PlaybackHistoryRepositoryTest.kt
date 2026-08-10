package com.baidu.tv.player.kt.repository

import app.cash.turbine.test
import com.baidu.tv.player.kt.database.PlaybackHistoryDao
import com.baidu.tv.player.kt.model.PlaybackHistory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PlaybackHistoryRepository 单测：插入重复 → 更新 lastPlayTime → 保留一条（MockK）。
 */
class PlaybackHistoryRepositoryTest {

    private val dao: PlaybackHistoryDao = mockk(relaxed = true)
    private val repo = PlaybackHistoryRepository(dao)

    private fun history(folder: String, lastPlayTime: Long = 0L) =
        PlaybackHistory(folderPath = folder, lastPlayTime = lastPlayTime, createTime = 0L)

    @Test
    fun insert_whenNoExisting_insertsNewRecordWithCurrentTime() = runTest {
        coEvery { dao.getHistoryByPath("/A") } returns null
        repo.insert(history("/A"))
        coVerify { dao.insert(any()) }
        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun insert_whenExisting_updatesLastPlayTime_keepsOneRecord() = runTest {
        val existing = history("/A", lastPlayTime = 100L)
        coEvery { dao.getHistoryByPath("/A") } returns existing
        repo.insert(history("/A"))
        coVerify { dao.update(any()) }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun delete_delegatesToDao() = runTest {
        val h = history("/A")
        repo.delete(h)
        coVerify { dao.delete(h) }
    }

    @Test
    fun deleteAll_delegatesToDao() = runTest {
        repo.deleteAll()
        coVerify { dao.deleteAll() }
    }

    @Test
    fun getTop4History_forwardsDaoFlow() = runTest {
        val flow = MutableStateFlow(emptyList<PlaybackHistory>())
        coEvery { dao.getTop4History() } returns flow
        repo.getTop4History().test {
            assertEquals(emptyList<PlaybackHistory>(), awaitItem())
            flow.emit(listOf(history("/A")))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
