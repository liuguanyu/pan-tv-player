package com.baidu.tv.player.ui.filebrowser;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.baidu.tv.player.R;
import com.baidu.tv.player.model.FileInfo;
import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 文件列表适配器
 */
public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
    private List<FileInfo> fileList;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private SimpleDateFormat dateFormat;
    private boolean multiSelectMode = false;
    private Set<String> selectedPaths = new HashSet<>();

    public FileAdapter() {
        this.fileList = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
    }

    public void setFileList(List<FileInfo> fileList) {
        this.fileList = fileList != null ? fileList : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.onItemLongClickListener = listener;
    }
    
    /**
     * 设置多选模式
     */
    public void setMultiSelectMode(boolean multiSelectMode) {
        this.multiSelectMode = multiSelectMode;
        notifyDataSetChanged();
    }
    
    /**
     * 设置选中的文件路径
     */
    public void setSelectedPaths(Set<String> selectedPaths) {
        this.selectedPaths = selectedPaths != null ? selectedPaths : new HashSet<>();
        notifyDataSetChanged();
    }
    
    /**
     * 获取选中的文件路径
     */
    public Set<String> getSelectedPaths() {
        return selectedPaths;
    }
    
    /**
     * 切换选中状态
     */
    public void toggleSelection(String path) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }
        notifyDataSetChanged();
    }
    
    /**
     * 清空选中
     */
    public void clearSelection() {
        selectedPaths.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileInfo file = fileList.get(position);
        holder.bind(file);
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    class FileViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivFileIcon;
        private TextView tvFileName;
        private TextView tvFileInfo;
        private ImageView ivSelected;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.iv_file_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileInfo = itemView.findViewById(R.id.tv_file_info);
            ivSelected = itemView.findViewById(R.id.iv_selected);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onItemClickListener != null) {
                    onItemClickListener.onItemClick(fileList.get(position), position);
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onItemLongClickListener != null) {
                    return onItemLongClickListener.onItemLongClick(fileList.get(position), position);
                }
                return false;
            });
        }

        public void bind(FileInfo file) {
            tvFileName.setText(file.getServerFilename());

            // 设置文件图标
            if (file.isDirectory()) {
                // 文件夹图标
                ivFileIcon.setImageResource(android.R.drawable.ic_menu_view);
            } else if (file.isImage()) {
                // 加载图片缩略图
                if (file.getThumbs() != null && file.getThumbs().getUrl1() != null) {
                    Glide.with(itemView.getContext())
                            .load(file.getThumbs().getUrl1())
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_gallery)
                            .into(ivFileIcon);
                } else {
                    ivFileIcon.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else if (file.isVideo()) {
                // 加载视频缩略图（如果有）
                if (file.getThumbs() != null && file.getThumbs().getUrl1() != null) {
                    Glide.with(itemView.getContext())
                            .load(file.getThumbs().getUrl1())
                            .placeholder(android.R.drawable.ic_media_play)
                            .error(android.R.drawable.ic_media_play)
                            .into(ivFileIcon);
                } else {
                    ivFileIcon.setImageResource(android.R.drawable.ic_media_play);
                }
            } else {
                // 其他文件图标
                ivFileIcon.setImageResource(android.R.drawable.ic_menu_info_details);
            }

            // 设置文件信息
            StringBuilder infoBuilder = new StringBuilder();
            
            if (file.isDirectory()) {
                infoBuilder.append("文件夹");
            } else {
                // 文件大小
                String size = Formatter.formatFileSize(itemView.getContext(), file.getSize());
                infoBuilder.append(size);
            }
            
            // 修改时间
            if (file.getServerMtime() > 0) {
                Date date = new Date(file.getServerMtime() * 1000);
                infoBuilder.append(" · ").append(dateFormat.format(date));
            }
            
            tvFileInfo.setText(infoBuilder.toString());

            // 选中状态
            if (multiSelectMode) {
                ivSelected.setVisibility(View.VISIBLE);
                if (selectedPaths.contains(file.getPath())) {
                    ivSelected.setImageResource(android.R.drawable.checkbox_on_background);
                } else {
                    ivSelected.setImageResource(android.R.drawable.checkbox_off_background);
                }
            } else {
                ivSelected.setVisibility(View.GONE);
            }
        }
    }

    public interface OnItemClickListener {
        void onItemClick(FileInfo file, int position);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(FileInfo file, int position);
    }
}