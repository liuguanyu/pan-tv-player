package com.baidu.tv.player.ui.filebrowser;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.baidu.tv.player.R;
import com.baidu.tv.player.model.FileInfo;
import com.baidu.tv.player.model.MediaType;
import com.baidu.tv.player.ui.playback.PlaybackActivity;
import com.baidu.tv.player.utils.PlaylistCache;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件浏览Fragment
 */
public class FileBrowserFragment extends Fragment {
    private FileBrowserViewModel viewModel;
    private FileAdapter adapter;
    private TextView tvCurrentPath;
    private Button btnRecursive;
    private Button btnSort;
    private Button btnPlaySelected;
    private ProgressBar progressLoading;
    private TextView tvEmptyMessage;
    
    private int mediaType;
    private String initialPath;

    public FileBrowserFragment() {
        // Required empty public constructor
    }

    public static FileBrowserFragment newInstance(int mediaType, String initialPath) {
        FileBrowserFragment fragment = new FileBrowserFragment();
        Bundle args = new Bundle();
        args.putInt("mediaType", mediaType);
        args.putString("initialPath", initialPath);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getArguments() != null) {
            mediaType = getArguments().getInt("mediaType", MediaType.ALL.getValue());
            initialPath = getArguments().getString("initialPath", "/");
        } else {
            mediaType = MediaType.ALL.getValue();
            initialPath = "/";
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_browser, container, false);
        
        initViews(view);
        initViewModel();
        setupRecyclerView(view);
        
        return view;
    }

    private void initViews(View view) {
        tvCurrentPath = view.findViewById(R.id.tv_current_path);
        btnRecursive = view.findViewById(R.id.btn_recursive);
        btnSort = view.findViewById(R.id.btn_sort);
        btnPlaySelected = view.findViewById(R.id.btn_play_selected);
        progressLoading = view.findViewById(R.id.progress_loading);
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message);
        
        // 递归按钮点击事件
        btnRecursive.setOnClickListener(v -> {
            boolean isRecursive = !viewModel.getIsLoading().getValue();
            viewModel.setRecursive(isRecursive);
            btnRecursive.setText(isRecursive ? "递归加载: 开" : "递归加载: 关");
            // 重新加载当前目录
            String currentPath = viewModel.getCurrentPath().getValue();
            if (currentPath != null) {
                viewModel.loadFileList(currentPath);
            }
        });
        
        // 排序按钮点击事件
        btnSort.setOnClickListener(v -> {
            viewModel.toggleSortMode();
        });
        
        // 播放当前列表按钮点击事件
        btnPlaySelected.setOnClickListener(v -> {
            List<FileInfo> currentList = viewModel.getFileList().getValue();
            if (currentList != null && !currentList.isEmpty()) {
                // 过滤出文件（不包括目录）
                List<FileInfo> filesToPlay = new ArrayList<>();
                for (FileInfo file : currentList) {
                    if (!file.isDirectory()) {
                        filesToPlay.add(file);
                    }
                }
                
                if (!filesToPlay.isEmpty()) {
                    startPlayback(filesToPlay);
                }
            }
        });
    }

    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(FileBrowserViewModel.class);
        viewModel.setMediaType(mediaType);
        
        // 观察文件列表
        viewModel.getFileList().observe(getViewLifecycleOwner(), files -> {
            adapter.setFileList(files);
            
            if (files == null || files.isEmpty()) {
                tvEmptyMessage.setVisibility(View.VISIBLE);
                tvEmptyMessage.setText("暂无文件");
            } else {
                tvEmptyMessage.setVisibility(View.GONE);
            }
        });
        
        // 观察加载状态
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });
        
        // 观察当前路径
        viewModel.getCurrentPath().observe(getViewLifecycleOwner(), path -> {
            tvCurrentPath.setText(path);
        });
        
        // 观察错误信息
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), errorMessage -> {
            if (errorMessage != null && !errorMessage.isEmpty()) {
                tvEmptyMessage.setVisibility(View.VISIBLE);
                tvEmptyMessage.setText(errorMessage);
            }
        });
        
        // 观察排序模式
        viewModel.getSortMode().observe(getViewLifecycleOwner(), sortMode -> {
            updateSortButtonText(sortMode);
        });
        
        // 加载初始文件列表
        viewModel.loadFileList(initialPath);
    }
    
    /**
     * 更新排序按钮文本
     */
    private void updateSortButtonText(FileBrowserViewModel.SortMode sortMode) {
        String text;
        switch (sortMode) {
            case NAME_ASC:
                text = "排序: 文件名↑";
                break;
            case NAME_DESC:
                text = "排序: 文件名↓";
                break;
            case DATE_ASC:
                text = "排序: 日期↑";
                break;
            case DATE_DESC:
                text = "排序: 日期↓";
                break;
            default:
                text = "排序: 文件名↑";
        }
        btnSort.setText(text);
    }

    private void setupRecyclerView(View view) {
        androidx.recyclerview.widget.RecyclerView recyclerView =
                view.findViewById(R.id.grid_files);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        
        adapter = new FileAdapter();
        adapter.setOnItemClickListener((file, position) -> {
            if (file.isDirectory()) {
                // 进入目录
                viewModel.enterDirectory(file.getPath());
            } else {
                // 在后台线程中生成播放列表（避免大目录导致UI卡顿）
                new Thread(() -> {
                    List<FileInfo> currentList = viewModel.getFileList().getValue();
                    if (currentList != null && !currentList.isEmpty()) {
                        // 过滤出文件（不包括目录）
                        List<FileInfo> filesToPlay = new ArrayList<>();
                        int clickedFileIndex = -1;
                        
                        for (int i = 0; i < currentList.size(); i++) {
                            FileInfo item = currentList.get(i);
                            if (!item.isDirectory()) {
                                filesToPlay.add(item);
                                // 记录点击文件在播放列表中的索引
                                if (item.getPath().equals(file.getPath())) {
                                    clickedFileIndex = filesToPlay.size() - 1;
                                }
                            }
                        }
                        
                        if (!filesToPlay.isEmpty()) {
                            int finalClickedFileIndex = clickedFileIndex;
                            // 回到主线程启动播放Activity
                            requireActivity().runOnUiThread(() -> {
                                startPlayback(filesToPlay, finalClickedFileIndex);
                            });
                        }
                    }
                }).start();
            }
        });
        
        recyclerView.setAdapter(adapter);
    }

    private void startPlayback(List<FileInfo> files) {
        startPlayback(files, 0);
    }
    
    private void startPlayback(List<FileInfo> files, int startIndex) {
        // 生成唯一的播放列表ID
        String playlistId = UUID.randomUUID().toString();
        // 将播放列表存入缓存
        PlaylistCache.getInstance().put(playlistId, files);
        
        Intent intent = new Intent(requireContext(), PlaybackActivity.class);
        // intent.putParcelableArrayListExtra("files", new ArrayList<>(files)); // 避免传递大数据
        intent.putExtra("playlistId", playlistId); // 传递缓存ID
        intent.putExtra("mediaType", mediaType);
        intent.putExtra("folderPath", viewModel.getCurrentPath().getValue());
        intent.putExtra("startIndex", startIndex); // 传递起始播放位置
        startActivity(intent);
    }

    /**
     * 处理返回键
     */
    public boolean onBackPressed() {
        if (viewModel.canGoBack()) {
            viewModel.goBack();
            return true;
        }
        return false;
    }
}