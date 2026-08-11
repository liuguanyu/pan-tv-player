package com.baidu.tv.player.kt.ui.main

import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ItemPlaylistCardBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class PlaylistCardViewHolder(
    val binding: ItemPlaylistCardBinding,
    private val accessTokenProvider: () -> String? = { null },
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(coverImagePath: String?, playlistName: String, stats: String) {
        if (!coverImagePath.isNullOrEmpty()) {
            val cover = if (coverImagePath.startsWith("http", ignoreCase = true)) {
                val uri = Uri.parse(coverImagePath)
                val host = uri.host.orEmpty().lowercase()
                val isBaiduUrl = host == "baidu.com" || host.endsWith(".baidu.com") ||
                    host == "baidupcs.com" || host.endsWith(".baidupcs.com")
                val token = accessTokenProvider()?.takeIf { it.isNotBlank() }
                if (isBaiduUrl && token != null &&
                    uri.getQueryParameter("access_token").isNullOrBlank()
                ) {
                    GlideUrl(
                        uri.buildUpon().appendQueryParameter("access_token", token).build().toString(),
                        LazyHeaders.Builder().addHeader("User-Agent", "pan.baidu.com").build(),
                    )
                } else {
                    coverImagePath
                }
            } else {
                coverImagePath
            }
            Glide.with(binding.ivCover.context)
                .load(cover)
                .placeholder(R.drawable.banner)
                .error(R.drawable.banner)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.ivCover)
        } else {
            binding.ivCover.setImageResource(R.drawable.banner)
        }
        binding.tvPlaylistName.text = playlistName
        binding.tvStats.text = stats
    }
}
