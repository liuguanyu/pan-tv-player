package com.baidu.tv.player.auth;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.baidu.tv.player.R;
import com.baidu.tv.player.model.DeviceCodeResponse;
import com.baidu.tv.player.ui.main.MainActivity;
import com.baidu.tv.player.utils.QRCodeUtils;

/**
 * 登录界面
 */
public class LoginActivity extends FragmentActivity {
    
    private AuthViewModel viewModel;
    private ImageView ivQrCode;
    private ProgressBar pbLoading;
    private LinearLayout llError;
    private Button btnRetry;
    private TextView tvStatus;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        initViews();
        initViewModel();
    }
    
    private void initViews() {
        ivQrCode = findViewById(R.id.iv_qr_code);
        pbLoading = findViewById(R.id.pb_loading);
        llError = findViewById(R.id.ll_error);
        btnRetry = findViewById(R.id.btn_retry);
        tvStatus = findViewById(R.id.tv_status);
        
        btnRetry.setOnClickListener(v -> viewModel.startLogin());
    }
    
    private void initViewModel() {
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        
        viewModel.getAuthState().observe(this, authState -> {
            switch (authState.getStatus()) {
                case LOADING:
                    showLoading();
                    tvStatus.setText(authState.getMessage());
                    break;
                    
                case DEVICE_CODE_RECEIVED:
                    DeviceCodeResponse response = (DeviceCodeResponse) authState.getData();
                    showQRCode(response);
                    // 开始轮询
                    viewModel.startPolling(response.getDeviceCode());
                    tvStatus.setText("请使用手机百度网盘APP扫描二维码登录");
                    break;
                    
                case POLLING:
                    // 保持二维码显示，状态不变
                    break;
                    
                case AUTHENTICATED:
                    Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                    startMainActivity();
                    break;
                    
                case UNAUTHENTICATED:
                    // 如果是刷新失败或其他原因导致的未认证，重新开始登录流程
                    viewModel.startLogin();
                    break;
                    
                case ERROR:
                    showError();
                    tvStatus.setText(authState.getMessage());
                    break;
            }
        });
        
        // 开始登录流程
        viewModel.startLogin();
    }
    
    private void showLoading() {
        pbLoading.setVisibility(View.VISIBLE);
        llError.setVisibility(View.GONE);
        ivQrCode.setImageBitmap(null);
    }
    
    private void showQRCode(DeviceCodeResponse response) {
        pbLoading.setVisibility(View.GONE);
        llError.setVisibility(View.GONE);
        
        String url = response.getFullVerificationUrl();
        if (url != null) {
            Bitmap qrCode = QRCodeUtils.createQRCodeBitmap(url, 250, 250);
            ivQrCode.setImageBitmap(qrCode);
        }
    }
    
    private void showError() {
        pbLoading.setVisibility(View.GONE);
        llError.setVisibility(View.VISIBLE);
        ivQrCode.setImageBitmap(null);
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}