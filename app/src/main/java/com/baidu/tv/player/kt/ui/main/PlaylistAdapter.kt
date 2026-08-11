package com.baidu.tv.player.kt.ui.main

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.baidu.tv.player.kt.databinding.ItemPlaylistCardBinding
import com.baidu.tv.player.kt.model.Playlist

class PlaylistAdapter(
    private val accessTokenProvider: () -> String? = { null },
) : RecyclerView.Adapter<PlaylistCardViewHolder>() {

    private val playlists = mutableListOf<Playlist>()
    private val refreshingPlaylistIds = mutableSetOf<Long>()

    var onItemClick: ((Playlist) -> Unit)? = null
    var onItemLongClick: ((Playlist) -> Unit)? = null
    var onDeleteClick: ((Playlist) -> Unit)? = null
    /** 卡片焦点变化回调：参数为当前获焦的播放列表，失去焦点时为 null。 */
    var onItemFocusChange: ((Playlist?) -> Unit)? = null

    /** 当前获焦卡片对应的播放列表，供菜单键等快捷操作定位目标。 */
    var focusedPlaylist: Playlist? = null
        private set

    var isEditMode: Boolean = false
        private set

    fun setPlaylists(newPlaylists: List<Playlist>) {
        playlists.clear()
        playlists.addAll(newPlaylists)
        notifyDataSetChanged()
    }

    fun setRefreshing(playlistId: Long, refreshing: Boolean) {
        if (refreshing) refreshingPlaylistIds += playlistId else refreshingPlaylistIds -= playlistId
        val position = playlists.indexOfFirst { it.id == playlistId }
        if (position >= 0) notifyItemChanged(position)
    }

    fun setEditMode(editMode: Boolean) {
        if (isEditMode == editMode) return
        isEditMode = editMode
        notifyDataSetChanged()
    }

    fun getItem(position: Int): Playlist = playlists[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistCardViewHolder {
        val binding = ItemPlaylistCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlaylistCardViewHolder(binding, accessTokenProvider)
    }

    override fun onBindViewHolder(holder: PlaylistCardViewHolder, position: Int) {
        val playlist = playlists[position]
        val stats = if (playlist.totalDuration > 0) {
            "${playlist.totalItems}个文件 · ${DateUtils.formatElapsedTime(playlist.totalDuration / 1000)}"
        } else {
            "${playlist.totalItems}个文件"
        }
        holder.bind(playlist.coverImagePath, playlist.name, stats)

        holder.binding.ivDelete.visibility = if (isEditMode) View.VISIBLE else View.GONE
        updateRefreshIndicator(holder, playlist)
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            focusedPlaylist = if (hasFocus) playlist else null
            onItemFocusChange?.invoke(focusedPlaylist)
            updateRefreshIndicator(holder, playlist)
        }

        holder.itemView.setOnClickListener { onItemClick?.invoke(playlist) }
        holder.itemView.setOnLongClickListener {
            // 刷新由卡片长按触发，避免 TV 焦点进入卡片内部；同步中禁止重复触发。
            if (playlist.id !in refreshingPlaylistIds) onItemLongClick?.invoke(playlist)
            true
        }
        holder.binding.ivDelete.setOnClickListener { onDeleteClick?.invoke(playlist) }
    }

    /**
     * 刷新指示：同步中显示转圈进度；未同步时刷新图标仅作「可长按同步」的视觉提示，
     * 焦点落在卡片上才浮现，避免常驻图标遮挡封面。
     */
    private fun updateRefreshIndicator(holder: PlaylistCardViewHolder, playlist: Playlist) {
        val isRefreshing = playlist.id in refreshingPlaylistIds
        holder.binding.progressRefresh.visibility = if (isRefreshing) View.VISIBLE else View.GONE
        holder.binding.ivRefresh.visibility =
            if (!isRefreshing && holder.itemView.isFocused) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = playlists.size
}
