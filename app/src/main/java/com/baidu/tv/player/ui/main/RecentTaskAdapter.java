package com.baidu.tv.player.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.baidu.tv.player.R;
import com.baidu.tv.player.model.MediaType;
import com.baidu.tv.player.model.PlaybackHistory;

import java.util.ArrayList;
import java.util.List;

/**
 * 最近任务适配器
 */
public class RecentTaskAdapter extends RecyclerView.Adapter<RecentTaskAdapter.ViewHolder> {
    
    private List<PlaybackHistory> historyList = new ArrayList<>();
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(PlaybackHistory history);
    }
    
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
    
    public void setHistoryList(List<PlaybackHistory> historyList) {
        this.historyList = historyList != null ? historyList : new ArrayList<>();
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_task, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlaybackHistory history = historyList.get(position);
        holder.bind(history);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(history);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return historyList.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFolderName;
        TextView tvFolderPath;
        TextView tvMediaType;
        TextView tvFileCount;
        
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFolderName = itemView.findViewById(R.id.tv_folder_name);
            tvFolderPath = itemView.findViewById(R.id.tv_folder_path);
            tvMediaType = itemView.findViewById(R.id.tv_media_type);
            tvFileCount = itemView.findViewById(R.id.tv_file_count);
        }
        
        void bind(PlaybackHistory history) {
            tvFolderName.setText(history.getFolderName());
            tvFolderPath.setText(history.getFolderPath());
            
            MediaType mediaType = MediaType.fromCode(history.getMediaType());
            tvMediaType.setText(mediaType.getName());
            
            tvFileCount.setText(history.getFileCount() + "个文件");
        }
    }
}