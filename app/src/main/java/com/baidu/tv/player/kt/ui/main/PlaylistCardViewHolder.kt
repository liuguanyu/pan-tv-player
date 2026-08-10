package com.baidu.tv.player.kt.ui.main

import androidx.recyclerview.widget.RecyclerView
import com.baidu.tv.player.kt.R
import com.baidu.tv.player.kt.databinding.ItemPlaylistCardBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions

class PlaylistCardViewHolder(
    val binding: ItemPlaylistCardBinding,
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(coverImagePath: String?, playlistName: String, stats: String) {
        if (!coverImagePath.isNullOrEmpty()) {
            Glide.with(binding.ivCover.context)
                .load(coverImagePath)
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
