package com.baidu.tv.player.geocoding;

import android.content.Context;

/**
 * 逆地理编码策略接口
 * 定义了将GPS坐标转换为地址信息的标准接口
 */
public interface GeocodingStrategy {
    
    /**
     * 获取策略名称
     * @return 策略名称（如：AMap、Geocoder、Nominatim）
     */
    String getName();
    
    /**
     * 检查策略是否可用
     * @param context Android上下文
     * @return true表示策略可用，false表示不可用
     */
    boolean isAvailable(Context context);
    
    /**
     * 获取策略的优先级
     * 数值越小，优先级越高
     * @return 优先级值（建议范围：1-100）
     */
    int getPriority();
    
    /**
     * 根据GPS坐标获取地址
     * @param context Android上下文
     * @param latitude 纬度（WGS84坐标系）
     * @param longitude 经度（WGS84坐标系）
     * @return 地址字符串，失败返回null
     */
    String getAddress(Context context, double latitude, double longitude);
    
    /**
     * 获取策略的超时时间（毫秒）
     * @return 超时时间
     */
    int getTimeout();
    
    /**
     * 获取策略的描述信息
     * @return 描述信息
     */
    String getDescription();
}