package com.baidu.tv.player.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.baidu.tv.player.model.AuthInfo;

/**
 * 认证视图模型
 */
public class AuthViewModel extends AndroidViewModel {
    
    private final AuthRepository authRepository;
    
    public AuthViewModel(@NonNull Application application) {
        super(application);
        authRepository = new AuthRepository(application);
    }
    
    /**
     * 获取认证状态
     */
    public LiveData<AuthRepository.AuthState> getAuthState() {
        return authRepository.getAuthStateLiveData();
    }
    
    /**
     * 开始登录流程（获取设备码）
     */
    public void startLogin() {
        authRepository.getDeviceCode();
    }
    
    /**
     * 开始轮询设备码
     */
    public void startPolling(String deviceCode) {
        authRepository.pollDeviceCodeStatus(deviceCode);
    }
    
    /**
     * 退出登录
     */
    public void logout() {
        authRepository.logout();
    }
    
    /**
     * 刷新token
     */
    public void refreshToken() {
        authRepository.refreshToken();
    }
    
    /**
     * 检查是否已认证
     */
    public boolean isAuthenticated() {
        return authRepository.isAuthenticated();
    }
    
    /**
     * 获取认证信息
     */
    public AuthInfo getAuthInfo() {
        return authRepository.getAuthInfo();
    }
    
    /**
     * 手动检查认证状态
     */
    public void checkAuthStatus() {
        authRepository.checkAuthStatus();
    }
}