package com.baidu.tv.player.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.List;

/**
 * 主界面Fragment
 */
public class MainFragment extends Fragment {
    
    private MainViewModel viewModel;
    private RecentTaskAdapter adapter;
    private LinearLayout btnImages;
    private LinearLayout btnVideos;
    private LinearLayout btnAll;
    private RecyclerView rvRecentTasks;
    private TextView tvRecentTitle;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        
        initViews(view);
        initViewModel();
        
        return view;
    }
    
    private void initViews(View view) {
        btnImages = view.findViewById(R.id.btn_images);
        btnVideos = view.findViewById(R.id.btn_videos);
        btnAll = view.findViewById(R.id.btn_all);
        TextView btnSettings = view.findViewById(R.id.btn_settings);
        rvRecentTasks = view.findViewById(R.id.rv_recent_tasks);
        tvRecentTitle = view.findViewById(R.id.tv_recent_title);
        
        // 设置RecyclerView为横向
        adapter = new RecentTaskAdapter();
        rvRecentTasks.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rvRecentTasks.setAdapter(adapter);
        
        // 设置点击事件
        btnImages.setOnClickListener(v -> openFileBrowser(MediaType.IMAGE));
        btnVideos.setOnClickListener(v -> openFileBrowser(MediaType.VIDEO));
        btnAll.setOnClickListener(v -> openFileBrowser(MediaType.ALL));
        btnSettings.setOnClickListener(v -> openSettings());
        
        // 设置最近任务点击事件
        adapter.setOnItemClickListener(this::onRecentTaskClick);
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
                adapter.setHistoryList(historyList);
            }
        });
    }
    
    private void openFileBrowser(MediaType mediaType) {
        Intent intent = new Intent(requireContext(), com.baidu.tv.player.ui.filebrowser.FileBrowserActivity.class);
        intent.putExtra("mediaType", mediaType.getValue());
        intent.putExtra("initialPath", "/");
        startActivity(intent);
    }
    
    private void onRecentTaskClick(PlaybackHistory history) {
        Intent intent = new Intent(requireContext(), com.baidu.tv.player.ui.playback.PlaybackActivity.class);
        intent.putExtra("historyId", history.getId());
        startActivity(intent);
    }
    
    /**
     * 打开设置界面
     */
    private void openSettings() {
        Intent intent = new Intent(requireContext(), com.baidu.tv.player.ui.settings.SettingsActivity.class);
        startActivity(intent);
    }
}