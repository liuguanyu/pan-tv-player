package com.baidu.tv.player.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences工具类
 */
public class PreferenceUtils {
    private static final String PREF_NAME = "baidu_tv_player";
    
    // 认证相关
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_TOKEN_EXPIRES_IN = "token_expires_in";
    private static final String KEY_TOKEN_TIME = "token_time";
    
    // 图片特效相关
    private static final String KEY_IMAGE_EFFECT = "image_effect";
    private static final String KEY_IMAGE_DISPLAY_DURATION = "image_display_duration";
    private static final String KEY_IMAGE_TRANSITION_DURATION = "image_transition_duration";
    
    // 地点显示
    private static final String KEY_SHOW_LOCATION = "show_location";
    
    // 文件排序相关
    private static final String KEY_FILE_SORT_MODE = "file_sort_mode";
    
    // 播放模式相关
    private static final String KEY_PLAY_MODE = "play_mode";

    // 默认值
    private static final int DEFAULT_IMAGE_EFFECT = 0; // 淡入淡出
    private static final int DEFAULT_IMAGE_DISPLAY_DURATION = 5000; // 5秒
    private static final int DEFAULT_IMAGE_TRANSITION_DURATION = 1000; // 1秒
    private static final boolean DEFAULT_SHOW_LOCATION = true;

    /**
     * 获取SharedPreferences实例
     */
    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ========== 认证相关 ==========

    /**
     * 保存访问令牌
     */
    public static void saveAccessToken(Context context, String accessToken) {
        getPreferences(context).edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .apply();
    }

    /**
     * 获取访问令牌
     */
    public static String getAccessToken(Context context) {
        return getPreferences(context).getString(KEY_ACCESS_TOKEN, "");
    }

    /**
     * 保存刷新令牌
     */
    public static void saveRefreshToken(Context context, String refreshToken) {
        getPreferences(context).edit()
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply();
    }

    /**
     * 获取刷新令牌
     */
    public static String getRefreshToken(Context context) {
        return getPreferences(context).getString(KEY_REFRESH_TOKEN, "");
    }

    /**
     * 保存令牌过期时间（秒）
     */
    public static void saveTokenExpiresIn(Context context, long expiresIn) {
        getPreferences(context).edit()
                .putLong(KEY_TOKEN_EXPIRES_IN, expiresIn)
                .apply();
    }

    /**
     * 获取令牌过期时间（秒）
     */
    public static long getTokenExpiresIn(Context context) {
        return getPreferences(context).getLong(KEY_TOKEN_EXPIRES_IN, 0);
    }

    /**
     * 保存令牌获取时间（毫秒）
     */
    public static void saveTokenTime(Context context, long time) {
        getPreferences(context).edit()
                .putLong(KEY_TOKEN_TIME, time)
                .apply();
    }

    /**
     * 获取令牌获取时间（毫秒）
     */
    public static long getTokenTime(Context context) {
        return getPreferences(context).getLong(KEY_TOKEN_TIME, 0);
    }

    /**
     * 清除认证信息
     */
    public static void clearAuthInfo(Context context) {
        getPreferences(context).edit()
                .remove(KEY_ACCESS_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_TOKEN_EXPIRES_IN)
                .remove(KEY_TOKEN_TIME)
                .apply();
    }

    /**
     * 检查令牌是否过期
     */
    public static boolean isTokenExpired(Context context) {
        long tokenTime = getTokenTime(context);
        long expiresIn = getTokenExpiresIn(context);
        
        if (tokenTime == 0 || expiresIn == 0) {
            return true;
        }
        
        long currentTime = System.currentTimeMillis();
        long expireTime = tokenTime + (expiresIn * 1000);
        
        // 提前5分钟认为过期
        return currentTime >= (expireTime - 5 * 60 * 1000);
    }

    // ========== 图片特效相关 ==========

    /**
     * 保存图片特效
     */
    public static void saveImageEffect(Context context, int effect) {
        getPreferences(context).edit()
                .putInt(KEY_IMAGE_EFFECT, effect)
                .apply();
    }

    /**
     * 获取图片特效
     */
    public static int getImageEffect(Context context) {
        return getPreferences(context).getInt(KEY_IMAGE_EFFECT, DEFAULT_IMAGE_EFFECT);
    }

    /**
     * 保存图片展示时长（毫秒）
     */
    public static void saveImageDisplayDuration(Context context, int duration) {
        getPreferences(context).edit()
                .putInt(KEY_IMAGE_DISPLAY_DURATION, duration)
                .apply();
    }

    /**
     * 获取图片展示时长（毫秒）
     */
    public static int getImageDisplayDuration(Context context) {
        return getPreferences(context).getInt(KEY_IMAGE_DISPLAY_DURATION, DEFAULT_IMAGE_DISPLAY_DURATION);
    }

    /**
     * 保存图片过渡时长（毫秒）
     */
    public static void saveImageTransitionDuration(Context context, int duration) {
        getPreferences(context).edit()
                .putInt(KEY_IMAGE_TRANSITION_DURATION, duration)
                .apply();
    }

    /**
     * 获取图片过渡时长（毫秒）
     */
    public static int getImageTransitionDuration(Context context) {
        return getPreferences(context).getInt(KEY_IMAGE_TRANSITION_DURATION, DEFAULT_IMAGE_TRANSITION_DURATION);
    }

    // ========== 地点显示相关 ==========

    /**
     * 保存是否显示地点
     */
    public static void saveShowLocation(Context context, boolean show) {
        getPreferences(context).edit()
                .putBoolean(KEY_SHOW_LOCATION, show)
                .apply();
    }

    /**
     * 获取是否显示地点
     */
    public static boolean getShowLocation(Context context) {
        return getPreferences(context).getBoolean(KEY_SHOW_LOCATION, DEFAULT_SHOW_LOCATION);
    }
    
    // ========== 文件排序相关 ==========
    
    /**
     * 保存文件排序模式
     * 0: NAME_ASC
     * 1: NAME_DESC
     * 2: DATE_ASC
     * 3: DATE_DESC
     */
    public static void saveFileSortMode(Context context, int mode) {
        getPreferences(context).edit()
                .putInt(KEY_FILE_SORT_MODE, mode)
                .apply();
    }
    
    /**
     * 获取文件排序模式
     */
    public static int getFileSortMode(Context context) {
        // 默认为0 (NAME_ASC)
        return getPreferences(context).getInt(KEY_FILE_SORT_MODE, 0);
    }
    
    // ========== 播放模式相关 ==========
    
    /**
     * 保存播放模式
     * 0: 顺序播放
     * 1: 随机播放
     * 2: 单曲循环
     * 3: 倒序播放
     */
    public static void savePlayMode(Context context, int mode) {
        getPreferences(context).edit()
                .putInt(KEY_PLAY_MODE, mode)
                .apply();
    }
    
    /**
     * 获取播放模式
     */
    public static int getPlayMode(Context context) {
        // 默认为0 (SEQUENTIAL)
        return getPreferences(context).getInt(KEY_PLAY_MODE, 0);
    }
}