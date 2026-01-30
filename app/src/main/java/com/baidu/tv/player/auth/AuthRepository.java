package com.baidu.tv.player.auth;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.baidu.tv.player.model.AuthInfo;
import com.baidu.tv.player.model.DeviceCodeResponse;

/**
 * 认证数据仓库
 */
public class AuthRepository {
    
    private final BaiduAuthService authService;
    private final MutableLiveData<AuthState> authStateLiveData;
    
    public AuthRepository(Context context) {
        this.authService = BaiduAuthService.getInstance(context);
        this.authStateLiveData = new MutableLiveData<>();
        
        // 初始化认证状态
        checkAuthStatus();
    }
    
    /**
     * 检查认证状态
     */
    public void checkAuthStatus() {
        if (authService.isAuthenticated()) {
            authStateLiveData.postValue(new AuthState(AuthState.Status.AUTHENTICATED, null));
        } else {
            authStateLiveData.postValue(new AuthState(AuthState.Status.UNAUTHENTICATED, null));
        }
    }
    
    /**
     * 获取设备码
     */
    public void getDeviceCode() {
        authStateLiveData.postValue(new AuthState(AuthState.Status.LOADING, "正在获取设备码..."));
        
        authService.getDeviceCode(new BaiduAuthService.AuthCallback<DeviceCodeResponse>() {
            @Override
            public void onSuccess(DeviceCodeResponse result) {
                authStateLiveData.postValue(new AuthState(AuthState.Status.DEVICE_CODE_RECEIVED, "设备码获取成功", result));
            }
            
            @Override
            public void onError(String error) {
                authStateLiveData.postValue(new AuthState(AuthState.Status.ERROR, error));
            }
        });
    }
    
    /**
     * 轮询设备码状态
     */
    public void pollDeviceCodeStatus(String deviceCode) {
        authStateLiveData.postValue(new AuthState(AuthState.Status.POLLING, "等待授权中..."));
        
        authService.pollDeviceCodeStatus(deviceCode, new BaiduAuthService.AuthCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (result) {
                    authStateLiveData.postValue(new AuthState(AuthState.Status.AUTHENTICATED, "授权成功"));
                } else {
                    authStateLiveData.postValue(new AuthState(AuthState.Status.ERROR, "授权失败"));
                }
            }
            
            @Override
            public void onError(String error) {
                authStateLiveData.postValue(new AuthState(AuthState.Status.ERROR, error));
            }
        });
    }
    
    /**
     * 刷新token
     */
    public void refreshToken() {
        authStateLiveData.postValue(new AuthState(AuthState.Status.REFRESHING, "正在刷新令牌..."));
        
        authService.refreshToken(new BaiduAuthService.AuthCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (result) {
                    authStateLiveData.postValue(new AuthState(AuthState.Status.AUTHENTICATED, "令牌刷新成功"));
                } else {
                    authStateLiveData.postValue(new AuthState(AuthState.Status.UNAUTHENTICATED, "令牌刷新失败"));
                }
            }
            
            @Override
            public void onError(String error) {
                authStateLiveData.postValue(new AuthState(AuthState.Status.ERROR, error));
            }
        });
    }
    
    /**
     * 退出登录
     */
    public void logout() {
        authService.logout();
        authStateLiveData.postValue(new AuthState(AuthState.Status.UNAUTHENTICATED, "已退出登录"));
    }
    
    /**
     * 获取认证状态LiveData
     */
    public LiveData<AuthState> getAuthStateLiveData() {
        return authStateLiveData;
    }
    
    /**
     * 获取认证信息
     */
    public AuthInfo getAuthInfo() {
        return authService.getAuthInfo();
    }
    
    /**
     * 检查是否已认证
     */
    public boolean isAuthenticated() {
        return authService.isAuthenticated();
    }
    
    /**
     * 获取访问令牌
     */
    public String getAccessToken() {
        return authService.getAccessToken();
    }
    
    /**
     * 认证状态类
     */
    public static class AuthState {
        public enum Status {
            LOADING,
            DEVICE_CODE_RECEIVED,
            POLLING,
            AUTHENTICATED,
            UNAUTHENTICATED,
            REFRESHING,
            ERROR
        }
        
        private final Status status;
        private final String message;
        private final Object data;
        
        public AuthState(Status status, String message) {
            this(status, message, null);
        }
        
        public AuthState(Status status, String message, Object data) {
            this.status = status;
            this.message = message;
            this.data = data;
        }
        
        public Status getStatus() {
            return status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Object getData() {
            return data;
        }
    }
}