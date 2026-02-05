package com.baidu.tv.player.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.module.AppGlideModule;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

/**
 * Glide 配置模块
 * 用于配置更长的超时时间和重试机制
 */
@GlideModule
public class GlideConfiguration extends AppGlideModule {
    private static final String TAG = "GlideConfiguration";
    
    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // 设置日志级别
        builder.setLogLevel(Log.INFO);
    }

    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // 创建日志拦截器
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
            @Override
            public void log(@NonNull String message) {
                Log.d(TAG, "OkHttp: " + message);
            }
        });
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        
        // 创建自定义的 OkHttpClient，配置更长的超时时间
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时30秒
                .readTimeout(30, TimeUnit.SECONDS)     // 读取超时30秒
                .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时30秒
                .retryOnConnectionFailure(true)        // 连接失败时重试
                .addInterceptor(loggingInterceptor)    // 添加日志拦截器
                .build();

        // 替换 Glide 的网络组件
        registry.replace(GlideUrl.class, InputStream.class,
                new OkHttpUrlLoader.Factory(client));
        
        Log.i(TAG, "Glide configured with extended timeouts (30s) and retry enabled");
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // 禁用清单解析以提高性能
        return false;
    }
}