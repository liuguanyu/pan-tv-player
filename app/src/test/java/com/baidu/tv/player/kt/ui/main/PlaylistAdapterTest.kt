package com.baidu.tv.player.kt.ui.main

import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ItemPlaylistCardBinding
import com.baidu.tv.player.kt.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistAdapterTest {

    @Test
    fun setPlaylists_updatesItemCountAndBindsData() {
        val adapter = PlaylistAdapter()
        val playlist = Playlist(id = 1L, name = "旅行", totalItems = 12, totalDuration = 120_000)
        adapter.setPlaylists(listOf(playlist))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)

        assertEquals(1, adapter.itemCount)
        assertEquals("旅行", holder.binding.tvPlaylistName.text.toString())
        assertTrue(holder.binding.tvStats.text.toString().contains("12个文件"))
    }

    @Test
    fun itemClick_invokesCallback() {
        val adapter = PlaylistAdapter()
        val playlist = Playlist(id = 2L, name = "点击")
        var clicked: Playlist? = null
        adapter.onItemClick = { clicked = it }
        adapter.setPlaylists(listOf(playlist))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)
        holder.itemView.performClick()

        assertEquals(playlist, clicked)
    }

    @Test
    fun itemLongClick_invokesRefreshCallback_withoutFocusingIcon() {
        val adapter = PlaylistAdapter()
        val playlist = Playlist(id = 3L, name = "长按")
        var longClicked: Playlist? = null
        adapter.onItemLongClick = { longClicked = it }
        adapter.setPlaylists(listOf(playlist))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)
        holder.itemView.performLongClick()

        assertEquals(playlist, longClicked)
        // 刷新图标默认隐藏，仅在卡片获焦或同步中浮现。
        assertEquals(android.view.View.GONE, holder.binding.ivRefresh.visibility)
        assertTrue(!holder.binding.ivRefresh.isFocusable)
    }

    @Test
    fun refreshIndicator_showsProgressWhileRefreshing() {
        val adapter = PlaylistAdapter()
        val playlist = Playlist(id = 5L, name = "同步中")
        adapter.setPlaylists(listOf(playlist))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)
        assertEquals(android.view.View.GONE, holder.binding.ivRefresh.visibility)
        assertEquals(android.view.View.GONE, holder.binding.progressRefresh.visibility)

        // 同步中：转圈进度显示，刷新图标隐藏。
        adapter.setRefreshing(playlist.id, true)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(android.view.View.VISIBLE, holder.binding.progressRefresh.visibility)
        assertEquals(android.view.View.GONE, holder.binding.ivRefresh.visibility)

        adapter.setRefreshing(playlist.id, false)
        adapter.onBindViewHolder(holder, 0)
        assertEquals(android.view.View.GONE, holder.binding.progressRefresh.visibility)
        assertEquals(android.view.View.GONE, holder.binding.ivRefresh.visibility)
    }

    @Test
    fun focusedPlaylist_tracksItemFocus() {
        val adapter = PlaylistAdapter()
        val playlist = Playlist(id = 6L, name = "焦点")
        adapter.setPlaylists(listOf(playlist))

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)
        assertEquals(null, adapter.focusedPlaylist)

        holder.itemView.onFocusChangeListener.onFocusChange(holder.itemView, true)
        assertEquals(playlist, adapter.focusedPlaylist)

        holder.itemView.onFocusChangeListener.onFocusChange(holder.itemView, false)
        assertEquals(null, adapter.focusedPlaylist)
    }

    @Test
    fun deleteClick_invokesDeleteCallbackInEditMode() {
        val adapter = PlaylistAdapter()
        val playlist = Playlist(id = 4L, name = "删除")
        var deleted: Playlist? = null
        adapter.onDeleteClick = { deleted = it }
        adapter.setPlaylists(listOf(playlist))
        adapter.setEditMode(true)

        val holder = createHolder()
        adapter.onBindViewHolder(holder, 0)
        holder.binding.ivDelete.performClick()

        assertEquals(playlist, deleted)
    }

    private fun createHolder(): PlaylistCardViewHolder {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = FrameLayout(context).apply {
            isFocusable = true
            isClickable = true
        }
        val cover = ImageView(context).apply { id = R.id.iv_cover }
        val delete = ImageView(context).apply { id = R.id.iv_delete }
        val refresh = ImageView(context).apply { id = R.id.iv_refresh }
        val progress = android.widget.ProgressBar(context).apply { id = R.id.progress_refresh }
        val name = TextView(context).apply { id = R.id.tv_playlist_name }
        val stats = TextView(context).apply { id = R.id.tv_stats }
        root.addView(cover)
        root.addView(delete)
        root.addView(refresh)
        root.addView(progress)
        root.addView(name)
        root.addView(stats)
        return PlaylistCardViewHolder(ItemPlaylistCardBinding.bind(root))
    }
}
