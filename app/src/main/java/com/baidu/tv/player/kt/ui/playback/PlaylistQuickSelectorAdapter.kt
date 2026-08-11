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

/** 播放页底部快速选播列表适配器（遥控器上键呼出）。 */
class PlaylistQuickSelectorAdapter : RecyclerView.Adapter<PlaylistQuickSelectorAdapter.VH>() {

    private var items: List<FileInfo> = emptyList()
    private var highlightedFileKey: String? = null
    private var onItemSelected: ((FileInfo) -> Unit)? = null

    /** 缩略图 URL 缓存：fsId → 已解析的图片 URL。 */
    private val thumbnailCache = mutableMapOf<Long, String>()

    init {
        // 缩略图异步返回及横向翻页都会触发 ViewHolder 回收；稳定 ID 保证焦点绑定到文件而非位置。
        setHasStableIds(true)
    }

    fun configure(
        items: List<FileInfo>,
        highlightedFile: FileInfo?,
        onItemSelected: (FileInfo) -> Unit,
    ) {
        this.items = items.toList()
        highlightedFileKey = highlightedFile?.stableKey()
        this.onItemSelected = onItemSelected
        thumbnailCache.clear()
        notifyDataSetChanged()
    }

    fun positionOf(file: FileInfo?): Int {
        val key = file?.stableKey() ?: return RecyclerView.NO_POSITION
        return items.indexOfFirst { it.stableKey() == key }
    }

    /** 只刷新新增缩略图对应的卡片，避免 notifyDataSetChanged 导致当前焦点节点被重新绑定。 */
    fun updateThumbnails(thumbnails: Map<Long, String>) {
        thumbnails.forEach { (fsId, url) ->
            if (thumbnailCache[fsId] == url) return@forEach
            thumbnailCache[fsId] = url
            items.forEachIndexed { position, file ->
                if (file.fsId == fsId) notifyItemChanged(position, PAYLOAD_THUMBNAIL)
            }
        }
    }

    override fun getItemId(position: Int): Long {
        val file = items[position]
        return if (file.fsId != 0L) file.fsId else file.stableKey().hashCode().toLong()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_quick_selector, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_THUMBNAIL)) {
            loadThumbnail(holder, items[position])
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        holder.fileName.text = file.serverFilename?.takeIf { it.isNotBlank() }
            ?: file.path?.substringAfterLast('/') ?: ""
        loadThumbnail(holder, file)

        val isCurrent = file.stableKey() == highlightedFileKey
        holder.focusBar.visibility = if (isCurrent) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onItemSelected?.invoke(items[pos])
        }
        holder.itemView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                holder.itemView.performClick()
                true
            } else {
                false
            }
        }
        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            val boundPosition = holder.bindingAdapterPosition
            val stillCurrent = boundPosition != RecyclerView.NO_POSITION &&
                items[boundPosition].stableKey() == highlightedFileKey
            holder.focusBar.visibility = if (hasFocus || stillCurrent) View.VISIBLE else View.GONE
        }
    }

    private fun loadThumbnail(holder: VH, file: FileInfo) {
        val thumbUrl = thumbnailCache[file.fsId] ?: resolveThumbsUrl(file)
        if (!thumbUrl.isNullOrEmpty()) {
            Glide.with(holder.thumbnail.context)
                .load(thumbUrl)
                .placeholder(R.drawable.bg_card_content)
                .error(R.drawable.bg_card_content)
                .centerCrop()
                .into(holder.thumbnail)
        } else {
            Glide.with(holder.thumbnail).clear(holder.thumbnail)
            holder.thumbnail.setImageResource(R.drawable.bg_card_content)
        }
    }

    private fun resolveThumbsUrl(file: FileInfo): String? = sequenceOf(
        file.thumbs?.icon,
        file.thumbs?.url1,
        file.thumbs?.url2,
        file.thumbs?.url3,
    ).filterNotNull().firstOrNull { it.isNotEmpty() && !it.contains("/file/") }

    private fun FileInfo.stableKey(): String =
        if (fsId != 0L) "fs:$fsId" else "path:${path.orEmpty()}"

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.quickSelectorThumbnail)
        val fileName: TextView = itemView.findViewById(R.id.quickSelectorFileName)
        val focusBar: View = itemView.findViewById(R.id.quickSelectorFocusBar)
    }

    companion object {
        private const val PAYLOAD_THUMBNAIL = "thumbnail"
    }
}
