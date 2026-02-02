package com.baidu.tv.player.ui.main;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.fragment.app.FragmentActivity;

import com.baidu.tv.player.R;
import com.baidu.tv.player.auth.AuthRepository;
import com.baidu.tv.player.ui.settings.SettingsActivity;

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
    
    /**
     * 处理按键事件
     * 按遥控器菜单键（或键盘M键）打开设置
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 遥控器菜单键或键盘M键打开设置
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_M) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}