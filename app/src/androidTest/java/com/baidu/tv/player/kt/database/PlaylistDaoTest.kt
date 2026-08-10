package com.baidu.tv.player.kt.database

import app.cash.turbine.test
import com.baidu.tv.player.kt.model.Playlist
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull

/**
 * PlaylistDao 集成测试：CRUD + Flow 采集。内存数据库。
 */
@HiltAndroidTest
class PlaylistDaoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var playlistDao: PlaylistDao

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() = runTest {
        playlistDao.deleteAll()
    }

    private fun playlist(name: String, order: Int = 0, createdAt: Long = 0L) =
        Playlist(name = name, createdAt = createdAt, sortOrder = order, mediaType = 1)

    @Test
    fun insert_thenQueryById_returnsPlaylist() = runTest {
        val id = playlistDao.insert(playlist("列表A", createdAt = 100L))
        val loaded = playlistDao.getPlaylistByIdSync(id)
        assertNotNull(loaded)
        assertEquals("列表A", loaded!!.name)
    }

    @Test
    fun update_changesName() = runTest {
        val id = playlistDao.insert(playlist("旧名"))
        val original = playlistDao.getPlaylistByIdSync(id)!!
        playlistDao.update(original.copy(name = "新名"))
        assertEquals("新名", playlistDao.getPlaylistByIdSync(id)!!.name)
    }

    @Test
    fun delete_removesPlaylist() = runTest {
        val id = playlistDao.insert(playlist("待删"))
        val p = playlistDao.getPlaylistByIdSync(id)!!
        playlistDao.delete(p)
        assertNull(playlistDao.getPlaylistByIdSync(id))
    }

    @Test
    fun getAllPlaylistsSync_orderedBySortOrderThenCreatedAtDesc() = runTest {
        playlistDao.insert(playlist("B", order = 1, createdAt = 50L))
        playlistDao.insert(playlist("A", order = 1, createdAt = 100L))
        playlistDao.insert(playlist("C", order = 0, createdAt = 10L))
        val all = playlistDao.getAllPlaylistsSync()
        // sortOrder ASC, createdAt DESC
        assertEquals(listOf("C", "A", "B"), all.map { it.name })
    }

    @Test
    fun getAllPlaylistsFlow_emitsOnInsertAndDelete() = runTest {
        playlistDao.getAllPlaylists().test {
            // 初始空
            assertEquals(emptyList<Playlist>(), awaitItem())
            val id = playlistDao.insert(playlist("列表A"))
            val first = awaitItem()
            assertEquals(1, first.size)
            assertEquals(id, first[0].id)
            playlistDao.delete(first[0])
            assertEquals(emptyList<Playlist>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
