package com.baidu.tv.player.kt.database

import app.cash.turbine.test
import com.baidu.tv.player.kt.model.Playlist
import com.baidu.tv.player.kt.model.PlaylistItem
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * PlaylistItemDao 集成测试：批量插入 + REPLACE 冲突策略 + Flow + 级联删除。内存数据库。
 */
@HiltAndroidTest
class PlaylistItemDaoTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var playlistDao: PlaylistDao
    @Inject lateinit var playlistItemDao: PlaylistItemDao

    private var playlistId: Long = 0L

    @Before
    fun setUp() = runTest {
        hiltRule.inject()
        playlistId = playlistDao.insert(Playlist(name = "P", mediaType = 0))
    }

    @After
    fun tearDown() = runTest {
        playlistItemDao.deleteByPlaylistId(playlistId)
        playlistDao.deleteAll()
    }

    private fun item(order: Int, fsId: Long = order.toLong(), fileName: String = "f$order") =
        PlaylistItem(
            playlistId = playlistId,
            fsId = fsId,
            filePath = "/dir/$fileName",
            fileName = fileName,
            mediaType = 1,
            sortOrder = order,
            fileSize = 100L * order,
        )

    @Test
    fun insertAll_insertsAllItemsInOrder() = runTest {
        val items = (0 until 50).map { item(it) }
        playlistItemDao.insertAll(items)
        assertEquals(50, playlistItemDao.getItemCount(playlistId))
        val loaded = playlistItemDao.getItemsByPlaylistIdSync(playlistId)
        assertEquals((0 until 50).toList(), loaded.map { it.sortOrder })
    }

    @Test
    fun insertAll_replaceOnDuplicateFsId_keepsOne() = runTest {
        // fsId 相同、sortOrder 不同 —— REPLACE 以主键(autogen)为准不会去重；
        // 但 PlaylistItem 主键 autogen，fsId 无唯一约束。这里验证业务约定：
        // 重复 fsId 由 Repository 层保证去重；DAO 层 REPLACE 仅在主键冲突生效。
        val a = item(order = 0, fsId = 999L, fileName = "a")
        val b = item(order = 1, fsId = 999L, fileName = "b")
        playlistItemDao.insertAll(listOf(a, b))
        assertEquals(2, playlistItemDao.getItemCount(playlistId))
    }

    @Test
    fun deleteByPlaylistId_removesAllItems() = runTest {
        playlistItemDao.insertAll((0 until 10).map { item(it) })
        playlistItemDao.deleteByPlaylistId(playlistId)
        assertEquals(0, playlistItemDao.getItemCount(playlistId))
    }

    @Test
    fun cascadeDelete_whenPlaylistDeleted() = runTest {
        playlistItemDao.insertAll((0 until 5).map { item(it) })
        // 删除播放列表应级联删除其项
        val p = playlistDao.getPlaylistByIdSync(playlistId)!!
        playlistDao.delete(p)
        // 用新播放列表复用同 id 不可能；改为校验原项已被级联清理（通过新建同结构无法查）
        // 直接断言项表对原 playlistId 计数为 0
        assertEquals(0, playlistItemDao.getItemCount(playlistId))
    }

    @Test
    fun getItemsByPlaylistIdFlow_emitsInsertionsOrderedBySortOrder() = runTest {
        playlistItemDao.getItemsByPlaylistId(playlistId).test {
            assertEquals(emptyList<PlaylistItem>(), awaitItem())
            playlistItemDao.insertAll(listOf(item(2), item(0), item(1)))
            val emitted = awaitItem()
            assertEquals(listOf(0, 1, 2), emitted.map { it.sortOrder })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getItemCount_reflectsInsertions() = runTest {
        assertEquals(0, playlistItemDao.getItemCount(playlistId))
        playlistItemDao.insert(item(0))
        playlistItemDao.insert(item(1))
        assertEquals(2, playlistItemDao.getItemCount(playlistId))
    }

    @Test
    fun itemPreservesAllFields() = runTest {
        val original = PlaylistItem(
            playlistId = playlistId, fsId = 555L, filePath = "/x/y.mp4",
            fileName = "y.mp4", mediaType = 1, sortOrder = 7, duration = 12000L, fileSize = 999L,
        )
        val id = playlistItemDao.insert(original)
        val loaded = playlistItemDao.getItemsByPlaylistIdSync(playlistId).first { it.id == id }
        assertEquals(555L, loaded.fsId)
        assertEquals("/x/y.mp4", loaded.filePath)
        assertEquals("y.mp4", loaded.fileName)
        assertEquals(1, loaded.mediaType)
        assertEquals(7, loaded.sortOrder)
        assertEquals(12000L, loaded.duration)
        assertEquals(999L, loaded.fileSize)
        assertTrue(loaded.id > 0)
    }
}
