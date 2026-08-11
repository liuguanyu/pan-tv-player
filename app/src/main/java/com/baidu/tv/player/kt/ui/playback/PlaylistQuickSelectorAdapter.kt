package com.baidu.tv.player.kt.ui.playback

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.model.FileInfo
import com.bumptech.glide.Glide

/**
 * 播放页底部快速选播列表适配器（遥控器上键呼出）。
 *
 * 以倒序展示播放列表，当前播放项高亮；方向键左右遍历，确定键选中并切换播放。
 *
 * 缩略图加载策略：
 * 1. 优先使用外部传入的缩略图缓存（由 Activity 通过 filemetas?thumb=1 批量获取）
 * 2. 其次使用 thumbs.icon / thumbs.urlX（跳过 /file/ 文件下载链接）
 * 3. 均不可用时展示占位图
 */
class PlaylistQuickSelectorAdapter : RecyclerView.Adapter<PlaylistQuickSelectorAdapter.VH>() {

    private var items: List<FileInfo> = emptyList()
    private var highlightedPosition: Int = -1
    private var onItemSelected: ((Int) -> Unit)? = null

    /** 缩略图 URL 缓存：fsId → 已解析的图片 URL */
    private val thumbnailCache = mutableMapOf<Long, String>()

    fun configure(
        items: List<FileInfo>,
        highlightedPosition: Int,
        onItemSelected: (Int) -> Unit,
    ) {
        this.items = items.toList()
        this.highlightedPosition = highlightedPosition
        this.onItemSelected = onItemSelected
        thumbnailCache.clear()
        notifyDataSetChanged()
    }

    /** 批量更新缩略图缓存并刷新列表。 */
    fun updateThumbnails(thumbnails: Map<Long, String>) {
        thumbnailCache.putAll(thumbnails)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_quick_selector, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        holder.fileName.text = file.serverFilename?.takeIf { it.isNotBlank() }
            ?: file.path?.substringAfterLast('/') ?: ""

        loadThumbnail(holder, file)

        // 焦点高亮：当前播放项或获得焦点时显示底部蓝条
        val isCurrent = position == highlightedPosition
        holder.focusBar.visibility = if (isCurrent) View.VISIBLE else View.GONE

        // 遥控器确认键（DPAD_CENTER / ENTER）
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onItemSelected?.invoke(pos)
                }
                true
            } else {
                false
            }
        }

        // 焦点变化时高亮/取消高亮
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            holder.focusBar.visibility = if (hasFocus || isCurrent) View.VISIBLE else View.GONE
        }
    }

    // ── 缩略图解析 ──────────────────────────────────────────────

    /** 加载缩略图：优先缓存，其次 thumbs 字段（跳过文件下载链接）。 */
    private fun loadThumbnail(holder: VH, file: FileInfo) {
        val thumbUrl = thumbnailCache[file.fsId] ?: resolveThumbsUrl(file)
        if (!thumbUrl.isNullOrEmpty()) {
            Glide.with(holder.thumbnail.context)
                .load(thumbUrl)
                .placeholder(R.drawable.bg_card_content)
                .centerCrop()
                .into(holder.thumbnail)
        } else {
            holder.thumbnail.setImageResource(R.drawable.bg_card_content)
        }
    }

    /** 从 thumbs 字段解析可用 URL，跳过包含 /file/ 的文件下载链接。 */
    private fun resolveThumbsUrl(file: FileInfo): String? {
        return sequenceOf(
            file.thumbs?.icon,
            file.thumbs?.url1,
            file.thumbs?.url2,
            file.thumbs?.url3,
        ).filterNotNull()
            .firstOrNull { it.isNotEmpty() && !it.contains("/file/") }
    }

    // ── 内部类型 ────────────────────────────────────────────────

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.quickSelectorThumbnail)
        val fileName: TextView = itemView.findViewById(R.id.quickSelectorFileName)
        val focusBar: View = itemView.findViewById(R.id.quickSelectorFocusBar)
    }
}
