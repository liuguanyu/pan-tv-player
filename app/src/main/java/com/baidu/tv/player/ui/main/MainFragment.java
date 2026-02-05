package com.baidu.tv.player.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.baidu.tv.player.R;
import com.baidu.tv.player.model.MediaType;
import com.baidu.tv.player.model.PlaybackHistory;
import com.baidu.tv.player.model.Playlist;
import com.baidu.tv.player.repository.PlaylistRepository;
import com.baidu.tv.player.utils.PreferenceUtils;

import java.util.List;

/**
 * 主界面Fragment
 */
public class MainFragment extends Fragment {
    
    private MainViewModel viewModel;
    private RecentTaskAdapter recentTaskAdapter;
    private PlaylistAdapter playlistAdapter;
    private PlaylistRepository playlistRepository;
    
    private RecyclerView rvPlaylists;
    private RecyclerView rvRecentTasks;
    private TextView tvRecentTitle;
    private TextView tvNoPlaylist;
    private ImageView btnBrowseFiles;
    private ImageView btnCreatePlaylist;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        
        initViews(view);
        initViewModel();
        loadPlaylists();
        
        return view;
    }
    
    private void initViews(View view) {
        rvPlaylists = view.findViewById(R.id.rv_playlists);
        rvRecentTasks = view.findViewById(R.id.rv_recent_tasks);
        tvRecentTitle = view.findViewById(R.id.tv_recent_title);
        tvNoPlaylist = view.findViewById(R.id.tv_no_playlist);
        btnBrowseFiles = view.findViewById(R.id.btn_browse_files);
        btnCreatePlaylist = view.findViewById(R.id.btn_create_playlist);
        
        // 设置播放列表RecyclerView为横向
        playlistAdapter = new PlaylistAdapter(requireContext());
        rvPlaylists.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvPlaylists.setAdapter(playlistAdapter);
        // 启用RecyclerView的焦点搜索
        rvPlaylists.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        rvPlaylists.setHasFixedSize(true);
        
        // 设置最近任务RecyclerView为横向
        recentTaskAdapter = new RecentTaskAdapter();
        rvRecentTasks.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvRecentTasks.setAdapter(recentTaskAdapter);
        // 启用RecyclerView的焦点搜索，但禁用RecyclerView本身的焦点（电视屏幕上可能不可见）
        rvRecentTasks.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        rvRecentTasks.setFocusable(false);
        
        // 设置点击事件
        btnBrowseFiles.setOnClickListener(v -> openFileBrowser(MediaType.ALL));
        btnCreatePlaylist.setOnClickListener(v -> openFileBrowserForPlaylist());
        
        // 设置播放列表点击事件
        playlistAdapter.setOnItemClickListener(this::onPlaylistClick);
        playlistAdapter.setOnItemLongClickListener(this::onPlaylistLongClick);
        playlistAdapter.setOnDeleteClickListener(this::onPlaylistDelete);
        playlistAdapter.setOnRefreshClickListener(this::onPlaylistRefresh);
        
        // 设置最近任务点击事件
        recentTaskAdapter.setOnItemClickListener(this::onRecentTaskClick);
    }
    
    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        
        viewModel.getRecentHistory().observe(getViewLifecycleOwner(), historyList -> {
            if (historyList == null || historyList.isEmpty()) {
                tvRecentTitle.setVisibility(View.GONE);
                rvRecentTasks.setVisibility(View.GONE);
            } else {
                tvRecentTitle.setVisibility(View.VISIBLE);
                rvRecentTasks.setVisibility(View.VISIBLE);
                recentTaskAdapter.setHistoryList(historyList);
            }
        });
    }
    
    /**
     * 加载播放列表
     */
    private void loadPlaylists() {
        playlistRepository = new PlaylistRepository(requireContext());
        playlistRepository.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists == null || playlists.isEmpty()) {
                rvPlaylists.setVisibility(View.GONE);
                tvNoPlaylist.setVisibility(View.VISIBLE);
                // 没有播放列表，默认聚焦到浏览文件按钮
                btnBrowseFiles.post(() -> btnBrowseFiles.requestFocus());
            } else {
                rvPlaylists.setVisibility(View.VISIBLE);
                tvNoPlaylist.setVisibility(View.GONE);
                playlistAdapter.setPlaylists(playlists);
                // 有播放列表，延迟请求第一个项的焦点
                rvPlaylists.post(() -> {
                    View firstChild = rvPlaylists.getLayoutManager().findViewByPosition(0);
                    if (firstChild != null) {
                        firstChild.requestFocus();
                    }
                });
            }
        });
    }
    
    /**
     * 打开文件浏览器（普通模式）
     */
    private void openFileBrowser(MediaType mediaType) {
        Intent intent = new Intent(requireContext(), com.baidu.tv.player.ui.filebrowser.FileBrowserActivity.class);
        intent.putExtra("mediaType", mediaType.getValue());
        intent.putExtra("initialPath", "/");
        startActivity(intent);
    }
    
    /**
     * 打开文件浏览器（创建播放列表模式）
     */
    private void openFileBrowserForPlaylist() {
        Intent intent = new Intent(requireContext(), com.baidu.tv.player.ui.filebrowser.FileBrowserActivity.class);
        intent.putExtra("mediaType", MediaType.ALL.getValue());
        intent.putExtra("initialPath", "/");
        intent.putExtra("multiSelectMode", true); // 多选模式
        startActivityForResult(intent, 1001); // 请求码用于标识创建播放列表操作
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        // 处理从FileBrowserActivity返回的结果
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK) {
            // 播放列表创建成功，刷新播放列表显示
            loadPlaylists();
        }
    }
    
    /**
     * 播放列表点击事件
     */
    private void onPlaylistClick(Playlist playlist) {
        // 如果处于编辑模式，点击无效
        if (playlistAdapter.isEditMode()) {
            return;
        }
        
        // 启动播放器，播放该播放列表
        Intent intent = new Intent(requireContext(), com.baidu.tv.player.ui.playback.PlaybackActivity.class);
        intent.putExtra("playlistDatabaseId", playlist.getId());
        startActivity(intent);
    }
    
    /**
     * 播放列表长按事件 - 切换编辑模式
     */
    private void onPlaylistLongClick(Playlist playlist) {
        // 切换编辑模式
        boolean newEditMode = !playlistAdapter.isEditMode();
        playlistAdapter.setEditMode(newEditMode);
        
        if (newEditMode) {
            android.widget.Toast.makeText(requireContext(),
                "点击删除按钮删除播放列表，再次长按退出编辑模式",
                android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 播放列表删除事件
     */
    private void onPlaylistDelete(Playlist playlist) {
        // 显示确认对话框
        new android.app.AlertDialog.Builder(requireContext())
            .setTitle("删除播放列表")
            .setMessage("确定要删除播放列表\"" + playlist.getName() + "\"吗？\n这将删除播放列表及其所有文件记录。")
            .setPositiveButton("删除", (dialog, which) -> {
                // 执行删除操作
                new Thread(() -> {
                    try {
                        playlistRepository.deletePlaylist(playlist,
                            () -> {
                                // 删除成功
                                requireActivity().runOnUiThread(() -> {
                                    android.widget.Toast.makeText(requireContext(),
                                        "播放列表已删除",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                    
                                    // 退出编辑模式
                                    playlistAdapter.setEditMode(false);
                                    
                                    // 刷新播放列表
                                    loadPlaylists();
                                });
                            },
                            () -> {
                                // 删除失败
                                requireActivity().runOnUiThread(() -> {
                                    android.widget.Toast.makeText(requireContext(),
                                        "删除播放列表失败",
                                        android.widget.Toast.LENGTH_SHORT).show();
                                });
                            });
                    } catch (Exception e) {
                        android.util.Log.e("MainFragment", "删除播放列表失败", e);
                        requireActivity().runOnUiThread(() -> {
                            android.widget.Toast.makeText(requireContext(),
                                "删除播放列表失败: " + e.getMessage(),
                                android.widget.Toast.LENGTH_SHORT).show();
                        });
                    }
                }).start();
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 播放列表刷新事件
     */
    private void onPlaylistRefresh(Playlist playlist) {
        // 显示刷新提示
        android.widget.Toast.makeText(requireContext(),
            "正在刷新播放列表\"" + playlist.getName() + "\"...",
            android.widget.Toast.LENGTH_SHORT).show();
        
        // 执行刷新操作（BaiduAuthService会在PlaylistRepository内部处理认证）
        playlistRepository.refreshPlaylist(playlist,
            () -> {
                // 刷新成功
                requireActivity().runOnUiThread(() -> {
                    android.widget.Toast.makeText(requireContext(),
                        "播放列表刷新成功",
                        android.widget.Toast.LENGTH_SHORT).show();
                    
                    // 刷新播放列表显示
                    loadPlaylists();
                });
            },
            () -> {
                // 刷新失败
                requireActivity().runOnUiThread(() -> {
                    android.widget.Toast.makeText(requireContext(),
                        "刷新播放列表失败，请检查网络连接或重新登录",
                        android.widget.Toast.LENGTH_SHORT).show();
                });
            });
    }
    
    /**
     * 最近任务点击事件
     */
    private void onRecentTaskClick(PlaybackHistory history) {
        Intent intent = new Intent(requireContext(), com.baidu.tv.player.ui.playback.PlaybackActivity.class);
        intent.putExtra("historyId", history.getId());
        startActivity(intent);
    }
    
    /**
     * 处理遥控器按键事件
     */
    @Override
    public void onResume() {
        super.onResume();
        // 设置按键监听
        getView().setFocusableInTouchMode(true);
        // 不让根视图获得焦点，而是让可见的子元素获得焦点
        // getView().requestFocus();
        // 确保焦点在可见的元素上
        requestFocusOnVisibleElement();
        getView().setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                // 菜单键打开设置
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    openSettings();
                    return true;
                }
                // 返回键：如果处于编辑模式，退出编辑模式
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (playlistAdapter.isEditMode()) {
                        playlistAdapter.setEditMode(false);
                        return true;
                    }
                }
            }
            return false;
        });
    }
    
    /**
     * 打开设置界面
     */
    private void openSettings() {
        Intent intent = new Intent(requireContext(), com.baidu.tv.player.ui.settings.SettingsActivity.class);
        startActivity(intent);
    }
    
    /**
     * 请求焦点到可见的元素
     */
    private void requestFocusOnVisibleElement() {
        // 延迟执行，确保布局已完成
        getView().post(() -> {
            // 优先聚焦到播放列表的第一个项
            if (rvPlaylists.getVisibility() == View.VISIBLE && rvPlaylists.getAdapter() != null
                && rvPlaylists.getAdapter().getItemCount() > 0) {
                View firstChild = rvPlaylists.getLayoutManager().findViewByPosition(0);
                if (firstChild != null && firstChild.requestFocus()) {
                    return;
                }
            }
            // 如果没有播放列表，聚焦到浏览文件按钮
            if (btnBrowseFiles.getVisibility() == View.VISIBLE && btnBrowseFiles.requestFocus()) {
                return;
            }
            // 最后尝试聚焦到创建播放列表按钮
            if (btnCreatePlaylist.getVisibility() == View.VISIBLE) {
                btnCreatePlaylist.requestFocus();
            }
        });
    }
}