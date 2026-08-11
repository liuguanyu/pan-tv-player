package com.baidu.tv.player.kt.ui.main

import android.net.Uri
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ItemRecentTaskBinding
import com.baidu.tv.player.kt.model.MediaType
import com.baidu.tv.player.kt.model.PlaybackHistory
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

class RecentTaskAdapter(
    private val accessTokenProvider: () -> String? = { null },
) : RecyclerView.Adapter<RecentTaskAdapter.ViewHolder>() {

    private val historyList = mutableListOf<PlaybackHistory>()

    var onItemClick: ((PlaybackHistory) -> Unit)? = null
    /** 卡片焦点变化回调：参数为是否有最近播放卡片获焦。 */
    var onItemFocusChange: ((Boolean) -> Unit)? = null

    fun setHistoryList(newHistoryList: List<PlaybackHistory>) {
        historyList.clear()
        historyList.addAll(newHistoryList)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): PlaybackHistory = historyList[position]

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, accessTokenProvider)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val history = historyList[position]
        holder.bind(history)
        holder.itemView.setOnClickListener { onItemClick?.invoke(history) }
        holder.itemView.setOnFocusChangeListener { _, hasFocus -> onItemFocusChange?.invoke(hasFocus) }
    }

    override fun getItemCount(): Int = historyList.size

    class ViewHolder(
        private val binding: ItemRecentTaskBinding,
        private val accessTokenProvider: () -> String?,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(history: PlaybackHistory) {
            binding.tvFolderName.text = history.folderName ?: history.folderPath.substringAfterLast('/').ifEmpty { history.folderPath }
            binding.tvFolderPath.text = history.folderPath
            binding.tvMediaType.text = MediaType.fromCode(history.mediaType).displayName
            binding.tvFileCount.text = "${history.fileCount}个文件"
            if (history.lastPlayTime > 0) {
                binding.tvLastPlayTime.visibility = View.VISIBLE
                binding.tvLastPlayTime.text = "\u00b7 " + DateUtils.getRelativeTimeSpanString(
                    history.lastPlayTime,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                )
            } else {
                binding.tvLastPlayTime.visibility = View.GONE
            }
            if (!history.coverImagePath.isNullOrEmpty() && !history.coverImagePath.contains("/file/")) {
                val cover = if (history.coverImagePath.startsWith("http", ignoreCase = true)) {
                    val coverUri = Uri.parse(history.coverImagePath)
                    val host = coverUri.host.orEmpty().lowercase()
                    val isBaiduUrl = host == "baidu.com" || host.endsWith(".baidu.com") ||
                        host == "baidupcs.com" || host.endsWith(".baidupcs.com")
                    val accessToken = accessTokenProvider()?.takeIf { it.isNotBlank() }
                    val authenticatedUrl = if (isBaiduUrl && accessToken != null &&
                        coverUri.getQueryParameter("access_token").isNullOrBlank()
                    ) {
                        coverUri.buildUpon().appendQueryParameter("access_token", accessToken).build().toString()
                    } else {
                        history.coverImagePath
                    }
                    GlideUrl(
                        authenticatedUrl,
                        LazyHeaders.Builder()
                            .addHeader("User-Agent", "pan.baidu.com")
                            .build(),
                    )
                } else {
                    history.coverImagePath
                }
                Glide.with(binding.ivBackground.context)
                    .load(cover)
                    .centerCrop()
                    .placeholder(R.drawable.banner)
                    .error(R.drawable.banner)
                    .timeout(30_000)
                    .into(binding.ivBackground)
            } else {
                binding.ivBackground.setImageResource(R.drawable.banner)
            }
        }
    }
}
