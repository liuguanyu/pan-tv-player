package com.baidu.tv.player.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.util.LruCache;

import java.util.HashMap;
import java.util.Map;

/**
 * 背景缓存管理器
 * 缓存图片的主色调和模糊背景，避免重复计算
 */
public class BackgroundCache {
    private static final String TAG = "BackgroundCache";
    private static BackgroundCache instance;
    
    // 颜色缓存：key = 图片URL, value = 主色调
    private final LruCache<String, Integer> colorCache;
    
    // 模糊背景缓存：key = 图片URL, value = 模糊后的Bitmap
    private final LruCache<String, Bitmap> blurCache;
    
    // 缓存大小限制
    private static final int MAX_COLOR_CACHE_SIZE = 100; // 最多缓存100个颜色
    private static final int MAX_BLUR_CACHE_SIZE = 20;   // 最多缓存20个模糊背景（占用内存较大）
    private static final int BLUR_CACHE_BYTES = 10 * 1024 * 1024; // 10MB
    
    private BackgroundCache() {
        // 使用 LRU 策略的颜色缓存
        colorCache = new LruCache<String, Integer>(MAX_COLOR_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Integer value) {
                return 1; // 每个颜色占用1个单位
            }
        };
        
        // 使用 LRU 策略的模糊背景缓存（基于内存大小）
        blurCache = new LruCache<String, Bitmap>(MAX_BLUR_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        
        Log.d(TAG, "背景缓存初始化完成");
    }
    
    /**
     * 获取单例实例
     */
    public static synchronized BackgroundCache getInstance() {
        if (instance == null) {
            instance = new BackgroundCache();
        }
        return instance;
    }
    
    /**
     * 缓存主色调
     */
    public void putColor(String imageUrl, int color) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        colorCache.put(imageUrl, color);
        Log.d(TAG, String.format("缓存主色调: %s -> #%06X", imageUrl, (0xFFFFFF & color)));
    }
    
    /**
     * 获取缓存的主色调
     */
    public Integer getColor(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        Integer color = colorCache.get(imageUrl);
        if (color != null) {
            Log.d(TAG, String.format("命中颜色缓存: %s -> #%06X", imageUrl, (0xFFFFFF & color)));
        }
        return color;
    }
    
    /**
     * 缓存模糊背景
     */
    public void putBlur(String imageUrl, Bitmap bitmap) {
        if (imageUrl == null || imageUrl.isEmpty() || bitmap == null) {
            return;
        }
        
        // 检查缓存大小
        if (blurCache.size() >= MAX_BLUR_CACHE_SIZE) {
            Log.d(TAG, "模糊背景缓存已满，将自动清理最旧的项");
        }
        
        blurCache.put(imageUrl, bitmap);
        Log.d(TAG, String.format("缓存模糊背景: %s (大小: %d bytes)", imageUrl, bitmap.getByteCount()));
    }
    
    /**
     * 获取缓存的模糊背景
     */
    public Bitmap getBlur(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return null;
        }
        Bitmap bitmap = blurCache.get(imageUrl);
        if (bitmap != null) {
            Log.d(TAG, String.format("命中模糊背景缓存: %s (大小: %d bytes)", imageUrl, bitmap.getByteCount()));
        }
        return bitmap;
    }
    
    /**
     * 清除指定图片的缓存
     */
    public void remove(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        colorCache.remove(imageUrl);
        Bitmap bitmap = blurCache.remove(imageUrl);
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
        Log.d(TAG, "清除缓存: " + imageUrl);
    }
    
    /**
     * 清除所有缓存
     */
    public void clear() {
        colorCache.evictAll();
        
        // 回收所有缓存的 Bitmap
        blurCache.evictAll();
        
        Log.d(TAG, "清除所有缓存");
    }
    
    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        int colorCount = colorCache.size();
        int blurCount = blurCache.size();
        long blurBytes = blurCache.size();
        
        return String.format(
            "背景缓存统计:\n" +
            "- 颜色缓存: %d/%d\n" +
            "- 模糊缓存: %d/%d (约 %.2f MB)\n" +
            "- 总内存占用: 约 %.2f MB",
            colorCount, MAX_COLOR_CACHE_SIZE,
            blurCount, MAX_BLUR_CACHE_SIZE, blurBytes / (1024.0 * 1024.0),
            blurBytes / (1024.0 * 1024.0)
        );
    }
    
    /**
     * 检查缓存是否已满
     */
    public boolean isFull() {
        return colorCache.size() >= MAX_COLOR_CACHE_SIZE && 
               blurCache.size() >= MAX_BLUR_CACHE_SIZE;
    }
}