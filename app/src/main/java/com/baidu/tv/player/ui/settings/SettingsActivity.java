package com.baidu.tv.player.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.baidu.tv.player.R;
import com.baidu.tv.player.auth.LoginActivity;
import com.baidu.tv.player.model.ImageEffect;
import com.baidu.tv.player.model.PlayMode;
import com.baidu.tv.player.utils.PreferenceUtils;

/**
 * 设置Activity
 */
public class SettingsActivity extends FragmentActivity {
    
    private RadioGroup rgImageEffectRow1;
    private RadioGroup rgImageEffectRow2;
    private SeekBar seekbarDisplayDuration;
    private TextView tvDisplayDuration;
    private Switch switchShowLocation;
    private RadioGroup rgPlayMode;
    private Button btnLogout;
    
    private boolean isUpdatingEffectSelection = false; // 防止递归触发

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        rgImageEffectRow1 = findViewById(R.id.rg_image_effect_row1);
        rgImageEffectRow2 = findViewById(R.id.rg_image_effect_row2);
        seekbarDisplayDuration = findViewById(R.id.seekbar_display_duration);
        tvDisplayDuration = findViewById(R.id.tv_display_duration);
        switchShowLocation = findViewById(R.id.switch_show_location);
        rgPlayMode = findViewById(R.id.rg_play_mode);
        btnLogout = findViewById(R.id.btn_logout);
    }

    private void loadSettings() {
        // 加载图片特效设置
        int imageEffect = PreferenceUtils.getImageEffect(this);
        // 清除两个RadioGroup的选中状态
        rgImageEffectRow1.clearCheck();
        rgImageEffectRow2.clearCheck();
        
        switch (imageEffect) {
            case 0:
                rgImageEffectRow1.check(R.id.rb_effect_fade);
                break;
            case 1:
                rgImageEffectRow1.check(R.id.rb_effect_ease);
                break;
            case 2:
                rgImageEffectRow1.check(R.id.rb_effect_float);
                break;
            case 3:
                rgImageEffectRow1.check(R.id.rb_effect_bounce);
                break;
            case 4:
                rgImageEffectRow2.check(R.id.rb_effect_random);
                break;
            case 5:
                rgImageEffectRow2.check(R.id.rb_effect_blinds);
                break;
            case 6:
                rgImageEffectRow2.check(R.id.rb_effect_zoom);
                break;
            case 7:
                rgImageEffectRow2.check(R.id.rb_effect_rotate);
                break;
            case 8:
                rgImageEffectRow2.check(R.id.rb_effect_slide);
                break;
        }
        
        // 加载图片展示时长设置（毫秒转换为秒）
        int displayDuration = PreferenceUtils.getImageDisplayDuration(this) / 1000;
        seekbarDisplayDuration.setProgress(displayDuration);
        tvDisplayDuration.setText(displayDuration + "秒");
        
        // 加载地点显示设置
        boolean showLocation = PreferenceUtils.getShowLocation(this);
        switchShowLocation.setChecked(showLocation);
        
        // 加载播放模式设置
        int playMode = PreferenceUtils.getPlayMode(this);
        switch (playMode) {
            case 0:
                rgPlayMode.check(R.id.rb_mode_sequential);
                break;
            case 1:
                rgPlayMode.check(R.id.rb_mode_random);
                break;
            case 2:
                rgPlayMode.check(R.id.rb_mode_single);
                break;
            case 3:
                rgPlayMode.check(R.id.rb_mode_reverse);
                break;
        }
    }

    private void setupListeners() {
        // 图片特效选择 - 第一行
        rgImageEffectRow1.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != -1 && !isUpdatingEffectSelection) {
                isUpdatingEffectSelection = true;
                rgImageEffectRow2.clearCheck();
                isUpdatingEffectSelection = false;
                saveImageEffect(checkedId);
            }
        });
        
        // 图片特效选择 - 第二行
        rgImageEffectRow2.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != -1 && !isUpdatingEffectSelection) {
                isUpdatingEffectSelection = true;
                rgImageEffectRow1.clearCheck();
                isUpdatingEffectSelection = false;
                saveImageEffect(checkedId);
            }
        });
        
        // 图片展示时长调整
        seekbarDisplayDuration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvDisplayDuration.setText(progress + "秒");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 不需要处理
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 保存设置（秒转换为毫秒）
                int duration = seekBar.getProgress() * 1000;
                PreferenceUtils.saveImageDisplayDuration(SettingsActivity.this, duration);
            }
        });
        
        // 地点显示开关
        switchShowLocation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PreferenceUtils.saveShowLocation(this, isChecked);
        });
        
        // 播放模式选择
        rgPlayMode.setOnCheckedChangeListener((group, modeCheckedId) -> {
            int mode;
            if (modeCheckedId == R.id.rb_mode_sequential) {
                mode = PlayMode.SEQUENTIAL.getValue();
            } else if (modeCheckedId == R.id.rb_mode_reverse) {
                mode = PlayMode.REVERSE.getValue();
            } else if (modeCheckedId == R.id.rb_mode_random) {
                mode = PlayMode.RANDOM.getValue();
            } else if (modeCheckedId == R.id.rb_mode_single) {
                mode = PlayMode.SINGLE.getValue();
            } else {
                mode = PlayMode.SEQUENTIAL.getValue();
            }
            PreferenceUtils.savePlayMode(this, mode);
        });
        
        // 退出登录
        btnLogout.setOnClickListener(v -> {
            // 清除认证信息
            PreferenceUtils.clearAuthInfo(this);
            
            // 跳转到登录界面
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
    
    /**
     * 保存图片特效
     */
    private void saveImageEffect(int checkedId) {
        int effect;
        if (checkedId == R.id.rb_effect_fade) {
            effect = ImageEffect.FADE.getValue();
        } else if (checkedId == R.id.rb_effect_ease) {
            effect = ImageEffect.EASE.getValue();
        } else if (checkedId == R.id.rb_effect_float) {
            effect = ImageEffect.FLOAT.getValue();
        } else if (checkedId == R.id.rb_effect_bounce) {
            effect = ImageEffect.BOUNCE.getValue();
        } else if (checkedId == R.id.rb_effect_blinds) {
            effect = ImageEffect.BLINDS.getValue();
        } else if (checkedId == R.id.rb_effect_zoom) {
            effect = ImageEffect.ZOOM.getValue();
        } else if (checkedId == R.id.rb_effect_rotate) {
            effect = ImageEffect.ROTATE.getValue();
        } else if (checkedId == R.id.rb_effect_slide) {
            effect = ImageEffect.SLIDE.getValue();
        } else if (checkedId == R.id.rb_effect_random) {
            effect = ImageEffect.RANDOM.getValue();
        } else {
            // 如果两个RadioGroup都没有选中任何项（例如在clearCheck()时触发），则不进行保存
            // 或者如果不匹配任何已知ID，则不保存
            // 这里我们简单处理，如果不匹配已知ID，就不保存变更，保持原样
            // 但考虑到用户体验，如果真的没有匹配到（比如clearCheck触发的-1），我们应该忽略
            if (checkedId == -1) {
                return;
            }
            effect = ImageEffect.FADE.getValue();
        }
        PreferenceUtils.saveImageEffect(this, effect);
    }
}