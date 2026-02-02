package com.baidu.tv.player.ui.filebrowser;

import android.os.Bundle;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.baidu.tv.player.R;
import com.baidu.tv.player.model.MediaType;

/**
 * 文件浏览Activity
 */
public class FileBrowserActivity extends FragmentActivity {
    private FileBrowserFragment fragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);
        
        // 获取传入的媒体类型
        int mediaType = getIntent().getIntExtra("mediaType", MediaType.ALL.getValue());
        String initialPath = getIntent().getStringExtra("initialPath");
        if (initialPath == null) {
            initialPath = "/";
        }
        
        // 获取多选模式参数
        boolean multiSelectMode = getIntent().getBooleanExtra("multiSelectMode", false);
        
        // 创建并添加Fragment
        fragment = FileBrowserFragment.newInstance(mediaType, initialPath, multiSelectMode);
        
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 处理返回键
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (fragment != null && fragment.onBackPressed()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}