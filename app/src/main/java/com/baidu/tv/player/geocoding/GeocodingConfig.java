package com.baidu.tv.player.geocoding;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 逆地理编码配置管理类
 * 负责管理用户对地理编码策略的偏好设置
 */
public class GeocodingConfig {
    
    private static final String PREFS_NAME = "geocoding_prefs";
    private static final String KEY_PREFERRED_STRATEGY = "preferred_strategy";
    private static final String KEY_FALLBACK_ENABLED = "fallback_enabled";
    
    private final SharedPreferences prefs;
    
    public GeocodingConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    /**
     * 设置首选策略
     * @param strategyName 策略名称
     */
    public void setPreferredStrategy(String strategyName) {
        prefs.edit().putString(KEY_PREFERRED_STRATEGY, strategyName).apply();
    }
    
    /**
     * 获取首选策略
     * @return 策略名称，如果未设置则返回null
     */
    public String getPreferredStrategy() {
        return prefs.getString(KEY_PREFERRED_STRATEGY, null);
    }
    
    /**
     * 设置是否启用回退机制
     * @param enabled true表示启用，false表示禁用
     */
    public void setFallbackEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FALLBACK_ENABLED, enabled).apply();
    }
    
    /**
     * 检查是否启用回退机制
     * @return true表示启用，false表示禁用
     */
    public boolean isFallbackEnabled() {
        return prefs.getBoolean(KEY_FALLBACK_ENABLED, true); // 默认启用
    }
    
    /**
     * 重置为默认配置
     */
    public void resetToDefault() {
        prefs.edit().clear().apply();
    }
}