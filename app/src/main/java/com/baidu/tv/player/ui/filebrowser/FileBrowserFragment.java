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
import com.baidu.tv.player.model.Playlist;
import com.baidu.tv.player.model.PlaylistItem;
import com.baidu.tv.player.repository.FileRepository;
import com.baidu.tv.player.repository.PlaylistRepository;
import com.baidu.tv.player.ui.playback.PlaybackActivity;
import com.baidu.tv.player.utils.PlaylistCache;
import com.baidu.tv.player.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private androidx.recyclerview.widget.RecyclerView recyclerView;
    
    private int mediaType;
    private String initialPath;
    private boolean multiSelectMode = false;

    public FileBrowserFragment() {
        // Required empty public constructor
    }

    public static FileBrowserFragment newInstance(int mediaType, String initialPath, boolean multiSelectMode) {
        FileBrowserFragment fragment = new FileBrowserFragment();
        Bundle args = new Bundle();
        args.putInt("mediaType", mediaType);
        args.putString("initialPath", initialPath);
        args.putBoolean("multiSelectMode", multiSelectMode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (getArguments() != null) {
            mediaType = getArguments().getInt("mediaType", MediaType.ALL.getValue());
            initialPath = getArguments().getString("initialPath", "/");
            multiSelectMode = getArguments().getBoolean("multiSelectMode", false);
        } else {
            mediaType = MediaType.ALL.getValue();
            initialPath = "/";
            multiSelectMode = false;
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
        
        // 根据模式调整按钮文本
        if (multiSelectMode) {
            btnPlaySelected.setText("确认选择");
            // 多选模式下显示操作提示
            android.widget.Toast.makeText(requireContext(),
                "提示：点击进入目录，长按选中目录",
                android.widget.Toast.LENGTH_LONG).show();
        } else {
            btnPlaySelected.setText("播放当前列表");
        }
        
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
        
        // 确认选择/播放当前列表按钮点击事件
        btnPlaySelected.setOnClickListener(v -> {
            if (multiSelectMode) {
                // 多选模式：确认选择并返回
                onConfirmSelection();
            } else {
                // 普通模式：播放当前列表
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
                
                // 列表更新时，滚动到顶部并让第一个项目获取焦点
                if (recyclerView != null) {
                    recyclerView.scrollToPosition(0);
                    // 延迟执行以确保布局已完成
                    recyclerView.postDelayed(() -> {
                        if (recyclerView.getChildCount() > 0) {
                            View child = recyclerView.getChildAt(0);
                            if (child != null) {
                                child.requestFocus();
                            }
                        }
                    }, 200);
                }
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
        recyclerView = view.findViewById(R.id.grid_files);
        
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        
        adapter = new FileAdapter();
        adapter.setMultiSelectMode(multiSelectMode);
        
        adapter.setOnItemClickListener((file, position) -> {
            if (multiSelectMode) {
                // 多选模式：目录可以进入，文件可以选中
                if (file.isDirectory()) {
                    // 进入目录
                    viewModel.enterDirectory(file.getPath());
                } else {
                    // 切换选中状态
                    adapter.toggleSelection(file.getPath());
                }
            } else {
                // 普通模式：原有逻辑
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
            }
        });

        // 设置长按事件（多选模式下用于选中目录）
        adapter.setOnItemLongClickListener((file, position) -> {
            if (multiSelectMode) {
                // 多选模式：长按选中任何项目（包括目录）
                adapter.toggleSelection(file.getPath());
                
                // 显示选中状态提示
                boolean isSelected = adapter.getSelectedPaths().contains(file.getPath());
                String msg = isSelected ? "已选中: " + file.getServerFilename() : "已取消: " + file.getServerFilename();
                android.widget.Toast.makeText(requireContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
                
                return true;
            }
            return false;
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
     * 确认选择（多选模式）
     */
    private void onConfirmSelection() {
        Set<String> selectedPaths = adapter.getSelectedPaths();
        if (selectedPaths.isEmpty()) {
            android.widget.Toast.makeText(requireContext(),
                "请至少选择一个目录或文件",
                android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示加载提示
        android.widget.Toast.makeText(requireContext(),
            "正在扫描文件，请稍候...",
            android.widget.Toast.LENGTH_SHORT).show();
        
        // 在后台线程执行文件扫描和播放列表创建
        new Thread(() -> {
            try {
                // 使用统一的认证服务获取访问令牌，确保令牌有效
                com.baidu.tv.player.auth.BaiduAuthService authService =
                    com.baidu.tv.player.auth.BaiduAuthService.getInstance(requireContext());
                String accessToken = authService.getAccessToken();
                FileRepository fileRepository = FileRepository.getInstance();
                PlaylistRepository playlistRepository = new PlaylistRepository(requireContext());
                
                // 获取当前文件列表，用于判断选中项是文件还是目录
                List<FileInfo> currentFiles = viewModel.getFileList().getValue();
                if (currentFiles == null) {
                    currentFiles = new ArrayList<>();
                }
                
                // 创建路径到FileInfo的映射
                java.util.Map<String, FileInfo> pathToFileMap = new java.util.HashMap<>();
                for (FileInfo file : currentFiles) {
                    pathToFileMap.put(file.getPath(), file);
                }
                
                // 收集所有媒体文件
                List<FileInfo> allMediaFiles = new ArrayList<>();
                Set<String> sourcePaths = new HashSet<>(); // 记录源目录用于刷新
                final boolean[] hasIgnoredSubDirs = {false}; // 记录是否忽略了子目录
                
                // 获取递归加载开关状态
                boolean isRecursiveEnabled = viewModel.getIsRecursive().getValue() != null
                    ? viewModel.getIsRecursive().getValue() : false;
                
                android.util.Log.d("FileBrowserFragment", "递归加载开关状态: " + isRecursiveEnabled);
                
                // 对每个选中的路径进行处理
                for (String selectedPath : selectedPaths) {
                    FileInfo selectedFile = pathToFileMap.get(selectedPath);
                    
                    android.util.Log.d("FileBrowserFragment", "处理选中路径: " + selectedPath + ", FileInfo: " + (selectedFile != null ? selectedFile.getServerFilename() : "null"));
                    
                    if (selectedFile != null && selectedFile.isDirectory()) {
                        // 目录：根据递归开关决定是否递归获取
                        sourcePaths.add(selectedPath);
                        
                        if (isRecursiveEnabled) {
                            // 递归模式：递归获取所有文件
                            android.util.Log.d("FileBrowserFragment", "递归模式：开始递归获取目录: " + selectedPath);
                            
                            // 使用回调方式递归获取文件
                            final List<FileInfo> dirFiles = new ArrayList<>();
                            final boolean[] completed = {false};
                            final boolean[] error = {false};
                            
                            fileRepository.fetchFilesRecursive(accessToken, selectedPath,
                                new FileRepository.FileListCallback() {
                                    @Override
                                    public void onSuccess(List<FileInfo> files) {
                                        android.util.Log.d("FileBrowserFragment", "递归获取成功，文件数量: " + (files != null ? files.size() : 0));
                                        if (files != null) {
                                            dirFiles.addAll(files);
                                            // 打印前5个文件的信息
                                            for (int i = 0; i < Math.min(5, files.size()); i++) {
                                                FileInfo f = files.get(i);
                                                android.util.Log.d("FileBrowserFragment", "  文件" + i + ": " + f.getServerFilename() +
                                                    ", isDir=" + f.isDirectory() + ", isImage=" + f.isImage() + ", isVideo=" + f.isVideo());
                                            }
                                        }
                                        completed[0] = true;
                                    }
                                    
                                    @Override
                                    public void onFailure(String errorMsg) {
                                        android.util.Log.e("FileBrowserFragment",
                                            "递归获取文件失败: " + errorMsg);
                                        error[0] = true;
                                        completed[0] = true;
                                    }
                                });
                            
                            // 等待异步操作完成（最多等待30秒）
                            int waitCount = 0;
                            while (!completed[0] && waitCount < 300) {
                                Thread.sleep(100);
                                waitCount++;
                            }
                            
                            android.util.Log.d("FileBrowserFragment", "等待完成，completed=" + completed[0] + ", error=" + error[0] + ", waitCount=" + waitCount);
                            
                            if (error[0]) {
                                android.util.Log.w("FileBrowserFragment", "跳过失败的目录: " + selectedPath);
                                continue; // 跳过失败的目录
                            }
                            
                            // 添加日志
                            android.util.Log.d("FileBrowserFragment", "递归获取到目录 " + selectedPath + " 的文件数量: " + dirFiles.size());
                            
                            // 过滤出媒体文件（排除目录）
                            int beforeFilterCount = dirFiles.size();
                            int afterFilterCount = 0;
                            for (FileInfo file : dirFiles) {
                                boolean isDir = file.isDirectory();
                                boolean isImg = file.isImage();
                                boolean isVid = file.isVideo();
                                String filename = file.getServerFilename();
                                
                                android.util.Log.d("FileBrowserFragment", "检查文件: " + filename +
                                    ", isDir=" + isDir + ", isImage=" + isImg + ", isVideo=" + isVid);
                                
                                if (!isDir && (isImg || isVid)) {
                                    allMediaFiles.add(file);
                                    afterFilterCount++;
                                }
                            }
                            android.util.Log.d("FileBrowserFragment", "目录 " + selectedPath + " 过滤前: " + beforeFilterCount + ", 过滤后: " + afterFilterCount);
                        } else {
                            // 非递归模式：只获取当前目录的文件
                            android.util.Log.d("FileBrowserFragment", "非递归模式：只获取当前目录的文件: " + selectedPath);
                            
                            // 使用回调方式获取当前目录的文件（非递归）
                            final List<FileInfo> dirFiles = new ArrayList<>();
                            final boolean[] completed = {false};
                            final boolean[] error = {false};
                            
                            fileRepository.fetchFilesNonRecursive(accessToken, selectedPath,
                                new FileRepository.FileListCallback() {
                                    @Override
                                    public void onSuccess(List<FileInfo> files) {
                                        android.util.Log.d("FileBrowserFragment", "获取当前目录成功，文件数量: " + (files != null ? files.size() : 0));
                                        if (files != null) {
                                            dirFiles.addAll(files);
                                            // 打印前5个文件的信息
                                            for (int i = 0; i < Math.min(5, files.size()); i++) {
                                                FileInfo f = files.get(i);
                                                android.util.Log.d("FileBrowserFragment", "  文件" + i + ": " + f.getServerFilename() +
                                                    ", isDir=" + f.isDirectory() + ", isImage=" + f.isImage() + ", isVideo=" + f.isVideo());
                                            }
                                        }
                                        completed[0] = true;
                                    }
                                    
                                    @Override
                                    public void onFailure(String errorMsg) {
                                        android.util.Log.e("FileBrowserFragment",
                                            "获取当前目录文件失败: " + errorMsg);
                                        error[0] = true;
                                        completed[0] = true;
                                    }
                                });
                            
                            // 等待异步操作完成（最多等待30秒）
                            int waitCount = 0;
                            while (!completed[0] && waitCount < 300) {
                                Thread.sleep(100);
                                waitCount++;
                            }
                            
                            android.util.Log.d("FileBrowserFragment", "等待完成，completed=" + completed[0] + ", error=" + error[0] + ", waitCount=" + waitCount);
                            
                            if (error[0]) {
                                android.util.Log.w("FileBrowserFragment", "跳过失败的目录: " + selectedPath);
                                continue; // 跳过失败的目录
                            }
                            
                            // 添加日志
                            android.util.Log.d("FileBrowserFragment", "获取到目录 " + selectedPath + " 的文件数量: " + dirFiles.size());
                            
                            // 过滤出媒体文件（排除目录）
                            int beforeFilterCount = dirFiles.size();
                            int afterFilterCount = 0;
                            for (FileInfo file : dirFiles) {
                                boolean isDir = file.isDirectory();
                                boolean isImg = file.isImage();
                                boolean isVid = file.isVideo();
                                String filename = file.getServerFilename();
                                
                                android.util.Log.d("FileBrowserFragment", "检查文件: " + filename +
                                    ", isDir=" + isDir + ", isImage=" + isImg + ", isVideo=" + isVid);
                                
                                if (!isDir && (isImg || isVid)) {
                                    allMediaFiles.add(file);
                                    afterFilterCount++;
                                }
                            }
                            android.util.Log.d("FileBrowserFragment", "目录 " + selectedPath + " 过滤前: " + beforeFilterCount + ", 过滤后: " + afterFilterCount);
                        }
                    } else {
                        // 文件：直接添加（如果是媒体文件）
                        if (selectedFile != null && !selectedFile.isDirectory()
                            && (selectedFile.isImage() || selectedFile.isVideo())) {
                            allMediaFiles.add(selectedFile);
                        }
                    }
                }
                
                android.util.Log.d("FileBrowserFragment", "最终收集到的媒体文件数量: " + allMediaFiles.size());
                // 打印前5个媒体文件的信息
                for (int i = 0; i < Math.min(5, allMediaFiles.size()); i++) {
                    FileInfo f = allMediaFiles.get(i);
                    android.util.Log.d("FileBrowserFragment", "  媒体文件" + i + ": " + f.getServerFilename());
                }
                
                if (allMediaFiles.isEmpty()) {
                    requireActivity().runOnUiThread(() -> {
                        android.widget.Toast.makeText(requireContext(),
                            "未找到任何媒体文件",
                            android.widget.Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // 生成播放列表名称（使用第一个目录名或默认名称）
                String playlistName = "新建播放列表";
                if (!sourcePaths.isEmpty()) {
                    String firstPath = sourcePaths.iterator().next();
                    playlistName = firstPath.substring(firstPath.lastIndexOf('/') + 1);
                }
                
                // 创建播放列表
                Playlist playlist = new Playlist();
                playlist.setName(playlistName);
                playlist.setCreatedAt(System.currentTimeMillis());
                playlist.setLastPlayedAt(0);
                playlist.setLastPlayedIndex(0);
                playlist.setMediaType(0); // 0=混合
                playlist.setTotalItems(allMediaFiles.size());
                playlist.setTotalDuration(0); // 暂不计算总时长
                
                // 生成封面（使用第一个有缩略图的图片或视频）
                String coverPath = null;
                for (FileInfo file : allMediaFiles) {
                    // 优先使用图片的缩略图
                    if (file.isImage() && file.getThumbs() != null && file.getThumbs().getUrl1() != null) {
                        coverPath = file.getThumbs().getUrl1();
                        android.util.Log.d("FileBrowserFragment", "使用图片缩略图作为封面: " + coverPath);
                        break;
                    }
                }
                // 如果没有图片缩略图，尝试使用视频缩略图
                if (coverPath == null) {
                    for (FileInfo file : allMediaFiles) {
                        if (file.isVideo() && file.getThumbs() != null && file.getThumbs().getUrl1() != null) {
                            coverPath = file.getThumbs().getUrl1();
                            android.util.Log.d("FileBrowserFragment", "使用视频缩略图作为封面: " + coverPath);
                            break;
                        }
                    }
                }
                // 如果都没有缩略图，使用默认封面
                if (coverPath == null) {
                    android.util.Log.d("FileBrowserFragment", "没有找到缩略图，使用默认封面");
                    coverPath = ""; // 空字符串表示使用默认封面
                }
                playlist.setCoverImagePath(coverPath);
                
                // 保存源目录路径（JSON格式）
                if (!sourcePaths.isEmpty()) {
                    try {
                        org.json.JSONArray jsonArray = new org.json.JSONArray(sourcePaths);
                        playlist.setSourcePaths(jsonArray.toString());
                    } catch (Exception e) {
                        android.util.Log.e("FileBrowserFragment",
                            "保存源路径失败", e);
                    }
                }
                
                // 插入播放列表到数据库 - 使用回调方式，避免线程阻塞和竞态条件
                playlistRepository.insertPlaylist(playlist, new PlaylistRepository.InsertCallback() {
                    @Override
                    public void onSuccess(long id) {
                        android.util.Log.d("FileBrowserFragment", "播放列表创建成功，ID=" + id);
                        
                        // 在后台线程准备播放列表项
                        new Thread(() -> {
                            try {
                                // 创建播放列表项
                                List<PlaylistItem> items = new ArrayList<>();
                                int sortOrder = 0;
                                for (FileInfo file : allMediaFiles) {
                                    PlaylistItem item = new PlaylistItem();
                                    item.setPlaylistId(id); // 使用正确的long类型ID
                                    item.setFilePath(file.getPath());
                                    item.setFileName(file.getServerFilename());
                                    item.setFsId(file.getFsId());
                                    item.setMediaType(file.isImage() ? 2 : 1); // 1=视频, 2=图片
                                    item.setSortOrder(sortOrder++);
                                    item.setDuration(file.getSize()); // 暂时用文件大小代替时长
                                    item.setFileSize(file.getSize());
                                    items.add(item);
                                }
                                
                                // 插入播放列表项到数据库
                                playlistRepository.insertPlaylistItems(items,
                                    () -> {
                                        android.util.Log.d("FileBrowserFragment", "播放列表项插入成功，数量: " + items.size());
                                        requireActivity().runOnUiThread(() -> {
                                            android.widget.Toast.makeText(requireContext(),
                                                "播放列表创建成功！\n共添加 " + allMediaFiles.size() + " 个文件",
                                                android.widget.Toast.LENGTH_SHORT).show();
                                            
                                            // 设置返回结果，通知MainFragment刷新播放列表
                                            android.content.Intent resultIntent = new android.content.Intent();
                                            requireActivity().setResult(android.app.Activity.RESULT_OK, resultIntent);
                                            
                                            // 返回首页
                                            requireActivity().finish();
                                        });
                                    },
                                    () -> {
                                        android.util.Log.e("FileBrowserFragment", "播放列表项插入失败");
                                        requireActivity().runOnUiThread(() -> {
                                            android.widget.Toast.makeText(requireContext(),
                                                "播放列表项保存失败",
                                                android.widget.Toast.LENGTH_SHORT).show();
                                        });
                                    });
                                    
                            } catch (Exception e) {
                                android.util.Log.e("FileBrowserFragment", "准备播放列表项失败", e);
                                requireActivity().runOnUiThread(() -> {
                                    android.widget.Toast.makeText(requireContext(),
                                        "准备数据失败: " + e.getMessage(),
                                        android.widget.Toast.LENGTH_SHORT).show();
                                });
                            }
                        }).start();
                    }
                    
                    @Override
                    public void onError(Exception e) {
                        android.util.Log.e("FileBrowserFragment", "创建播放列表失败", e);
                        requireActivity().runOnUiThread(() -> {
                            android.widget.Toast.makeText(requireContext(),
                                "创建播放列表失败: " + e.getMessage(),
                                android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                });
                
            } catch (Exception e) {
                android.util.Log.e("FileBrowserFragment",
                    "创建播放列表失败", e);
                requireActivity().runOnUiThread(() -> {
                    android.widget.Toast.makeText(requireContext(),
                        "创建播放列表失败: " + e.getMessage(),
                        android.widget.Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * 处理返回键
     */
    public boolean onBackPressed() {
        if (multiSelectMode) {
            // 多选模式下返回键清空选择或退出
            if (!adapter.getSelectedPaths().isEmpty()) {
                adapter.clearSelection();
                return true;
            }
        }
        
        if (viewModel.canGoBack()) {
            viewModel.goBack();
            return true;
        }
        return false;
    }
}