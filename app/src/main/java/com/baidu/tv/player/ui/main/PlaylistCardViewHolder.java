package com.baidu.tv.player.ui.main;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.baidu.tv.player.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

/**
 * 播放列表卡片ViewHolder
 */
public class PlaylistCardViewHolder extends RecyclerView.ViewHolder {
    public ImageView ivCover;
    public TextView tvPlaylistName;
    public TextView tvStats;
    public ImageView ivDelete;
    public ImageView ivRefresh;

    public PlaylistCardViewHolder(View itemView) {
        super(itemView);
        ivCover = itemView.findViewById(R.id.iv_cover);
        tvPlaylistName = itemView.findViewById(R.id.tv_playlist_name);
        tvStats = itemView.findViewById(R.id.tv_stats);
        ivDelete = itemView.findViewById(R.id.iv_delete);
        ivRefresh = itemView.findViewById(R.id.iv_refresh);
    }

    /**
     * 绑定数据到视图
     */
    public void bind(String coverImagePath, String playlistName, String stats) {
        // 加载封面图片
        if (coverImagePath != null && !coverImagePath.isEmpty()) {
            Glide.with(ivCover.getContext())
                    .load(coverImagePath)
                    .centerCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(ivCover);
        } else {
            // 使用默认图片
            ivCover.setImageResource(R.drawable.banner);
        }

        // 设置播放列表名称
        tvPlaylistName.setText(playlistName);

        // 设置统计信息
        tvStats.setText(stats);
    }
}