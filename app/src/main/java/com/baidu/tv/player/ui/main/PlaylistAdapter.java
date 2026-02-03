package com.baidu.tv.player.ui.main;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.baidu.tv.player.R;
import com.baidu.tv.player.model.Playlist;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放列表适配器
 */
public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistCardViewHolder> {
    private List<Playlist> playlists = new ArrayList<>();
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private OnDeleteClickListener onDeleteClickListener;
    private OnRefreshClickListener onRefreshClickListener;
    private final Context context;
    private boolean isEditMode = false;

    public PlaylistAdapter(Context context) {
        this.context = context;
    }

    public void setPlaylists(List<Playlist> playlists) {
        this.playlists = playlists;
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }
    
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }
    
    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.onDeleteClickListener = listener;
    }
    
    public void setOnRefreshClickListener(OnRefreshClickListener listener) {
        this.onRefreshClickListener = listener;
    }
    
    /**
     * 设置编辑模式
     */
    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        notifyDataSetChanged();
    }
    
    /**
     * 检查是否处于编辑模式
     */
    public boolean isEditMode() {
        return isEditMode;
    }

    @NonNull
    @Override
    public PlaylistCardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist_card, parent, false);
        return new PlaylistCardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistCardViewHolder holder, int position) {
        Playlist playlist = playlists.get(position);
        
        // 格式化统计信息
        String stats;
        if (playlist.getTotalDuration() > 0) {
            String duration = DateUtils.formatElapsedTime(playlist.getTotalDuration() / 1000);
            stats = playlist.getTotalItems() + "个文件 · " + duration;
        } else {
            stats = playlist.getTotalItems() + "个文件";
        }
        
        holder.bind(playlist.getCoverImagePath(), playlist.getName(), stats);
        
        // 设置删除按钮和刷新按钮的可见性
        if (isEditMode) {
            holder.ivDelete.setVisibility(View.VISIBLE);
            holder.ivRefresh.setVisibility(View.VISIBLE);
        } else {
            holder.ivDelete.setVisibility(View.GONE);
            holder.ivRefresh.setVisibility(View.GONE);
        }
        
        // 点击事件
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(playlist);
            }
        });
        
        // 长按事件
        holder.itemView.setOnLongClickListener(v -> {
            if (onItemLongClickListener != null) {
                onItemLongClickListener.onItemLongClick(playlist);
                return true;
            }
            return false;
        });
        
        // 删除按钮点击事件
        holder.ivDelete.setOnClickListener(v -> {
            if (onDeleteClickListener != null) {
                onDeleteClickListener.onDeleteClick(playlist);
            }
        });
        
        // 刷新按钮点击事件
        holder.ivRefresh.setOnClickListener(v -> {
            if (onRefreshClickListener != null) {
                onRefreshClickListener.onRefreshClick(playlist);
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlists.size();
    }

    public interface OnItemClickListener {
        void onItemClick(Playlist playlist);
    }
    
    public interface OnItemLongClickListener {
        void onItemLongClick(Playlist playlist);
    }
    
    public interface OnDeleteClickListener {
        void onDeleteClick(Playlist playlist);
    }
    
    public interface OnRefreshClickListener {
        void onRefreshClick(Playlist playlist);
    }
}