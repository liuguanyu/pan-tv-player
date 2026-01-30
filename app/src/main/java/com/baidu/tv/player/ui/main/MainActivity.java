package com.baidu.tv.player.ui.main;

import android.content.Intent;
import android.os.Bundle;

import androidx.fragment.app.FragmentActivity;

import com.baidu.tv.player.R;
import com.baidu.tv.player.auth.AuthRepository;

/**
 * 主Activity
 */
public class MainActivity extends FragmentActivity {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 检查是否已登录
        AuthRepository authRepository = new AuthRepository(this);
        if (!authRepository.isAuthenticated()) {
            // 未登录，跳转到登录界面
            Intent intent = new Intent(this, com.baidu.tv.player.auth.LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }
        
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_browse_fragment, new MainFragment())
                    .commitNow();
        }
    }
}