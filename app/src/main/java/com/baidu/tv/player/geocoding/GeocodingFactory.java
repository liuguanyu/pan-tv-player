package com.baidu.tv.player.geocoding;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 逆地理编码策略工厂
 * 负责管理和提供最佳的逆地理编码策略
 */
public class GeocodingFactory {
    
    private static final String TAG = "GeocodingFactory";
    private static GeocodingFactory instance;
    private final List<GeocodingStrategy> strategies;
    
    private GeocodingFactory() {
        strategies = new ArrayList<>();
        
        // 注册所有可用策略
        registerStrategy(new AmapGeocodingStrategy());
        registerStrategy(new AndroidGeocoderStrategy());
        registerStrategy(new NominatimGeocodingStrategy());
        
        // 按优先级排序（数值越小优先级越高）
        Collections.sort(strategies, new Comparator<GeocodingStrategy>() {
            @Override
            public int compare(GeocodingStrategy s1, GeocodingStrategy s2) {
                return Integer.compare(s1.getPriority(), s2.getPriority());
            }
        });
        
        Log.d(TAG, "初始化完成，已注册 " + strategies.size() + " 个策略");
    }
    
    public static synchronized GeocodingFactory getInstance() {
        if (instance == null) {
            instance = new GeocodingFactory();
        }
        return instance;
    }
    
    /**
     * 注册新的策略
     * @param strategy 策略实例
     */
    public void registerStrategy(GeocodingStrategy strategy) {
        if (strategy != null) {
            strategies.add(strategy);
            Log.d(TAG, "注册策略: " + strategy.getName());
        }
    }
    
    /**
     * 获取最佳的地址信息
     * 会依次尝试所有可用策略，直到获取成功
     * @param context Android上下文
     * @param latitude 纬度
     * @param longitude 经度
     * @return 地址字符串，如果所有策略都失败则返回null
     */
    public String getAddress(Context context, double latitude, double longitude) {
        GeocodingConfig config = new GeocodingConfig(context);
        String preferredStrategy = config.getPreferredStrategy();
        boolean fallbackEnabled = config.isFallbackEnabled();
        
        // 如果配置了首选策略，先尝试使用首选策略
        if (preferredStrategy != null && !preferredStrategy.isEmpty()) {
            GeocodingStrategy preferred = getStrategy(preferredStrategy);
            if (preferred != null) {
                try {
                    if (preferred.isAvailable(context)) {
                        Log.d(TAG, "使用首选策略: " + preferred.getName());
                        String address = preferred.getAddress(context, latitude, longitude);
                        if (address != null && !address.isEmpty()) {
                            Log.d(TAG, "首选策略 " + preferred.getName() + " 成功获取地址");
                            return address;
                        }
                        Log.d(TAG, "首选策略 " + preferred.getName() + " 未能获取地址");
                    } else {
                        Log.d(TAG, "首选策略 " + preferred.getName() + " 当前不可用");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "首选策略 " + preferred.getName() + " 执行出错: " + e.getMessage());
                }
                
                // 如果禁用了回退机制，直接返回
                if (!fallbackEnabled) {
                    Log.d(TAG, "回退机制已禁用，不尝试其他策略");
                    return null;
                }
            }
        }
        
        // 如果首选策略失败或未配置，按优先级尝试所有策略
        Log.d(TAG, "使用默认策略序列");
        for (GeocodingStrategy strategy : strategies) {
            // 跳过已经尝试过的首选策略
            if (preferredStrategy != null && strategy.getName().equals(preferredStrategy)) {
                continue;
            }
            
            try {
                if (strategy.isAvailable(context)) {
                    Log.d(TAG, "尝试使用策略: " + strategy.getName());
                    String address = strategy.getAddress(context, latitude, longitude);
                    if (address != null && !address.isEmpty()) {
                        Log.d(TAG, "策略 " + strategy.getName() + " 成功获取地址");
                        return address;
                    }
                    Log.d(TAG, "策略 " + strategy.getName() + " 未能获取地址");
                } else {
                    Log.d(TAG, "策略 " + strategy.getName() + " 当前不可用");
                }
            } catch (Exception e) {
                Log.e(TAG, "策略 " + strategy.getName() + " 执行出错: " + e.getMessage());
            }
        }
        
        Log.e(TAG, "所有策略均未能获取地址");
        return null;
    }
    
    /**
     * 获取指定名称的策略
     * @param name 策略名称
     * @return 策略实例，未找到返回null
     */
    public GeocodingStrategy getStrategy(String name) {
        for (GeocodingStrategy strategy : strategies) {
            if (strategy.getName().equals(name)) {
                return strategy;
            }
        }
        return null;
    }
    
    /**
     * 获取所有可用策略列表
     * @return 策略列表
     */
    public List<GeocodingStrategy> getAllStrategies() {
        return new ArrayList<>(strategies);
    }
}