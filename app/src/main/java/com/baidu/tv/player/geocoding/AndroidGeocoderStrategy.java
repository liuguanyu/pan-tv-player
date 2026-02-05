package com.baidu.tv.player.geocoding;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

import java.util.List;
import java.util.Locale;

/**
 * Android原生Geocoder策略
 * 优点：无需API Key、系统内置、支持离线
 * 缺点：在中国境内依赖Google服务、可能不可用
 */
public class AndroidGeocoderStrategy implements GeocodingStrategy {
    
    private static final String TAG = "AndroidGeocoderStrategy";
    private static final int TIMEOUT = 5000; // 5秒
    
    @Override
    public String getName() {
        return "AndroidGeocoder";
    }
    
    @Override
    public boolean isAvailable(Context context) {
        return Geocoder.isPresent();
    }
    
    @Override
    public int getPriority() {
        return 2; // 中等优先级
    }
    
    @Override
    public String getAddress(Context context, double latitude, double longitude) {
        try {
            Log.d(TAG, "开始Android Geocoder逆地理编码");
            
            Geocoder geocoder = new Geocoder(context, Locale.CHINESE);
            
            // 获取多个结果以找到最详细的地址
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 3);
            
            if (addresses != null && !addresses.isEmpty()) {
                Log.d(TAG, "Geocoder返回了 " + addresses.size() + " 个结果");
                
                // 遍历所有结果，找到最详细的地址
                for (int i = 0; i < addresses.size(); i++) {
                    Address address = addresses.get(i);
                    
                    // 调试：打印地址信息
                    Log.d(TAG, String.format("地址[%d]: %s, %s, %s", 
                            i, address.getFeatureName(), address.getThoroughfare(), address.getLocality()));
                    
                    StringBuilder sb = new StringBuilder();
                    
                    // 优先使用 featureName (建筑物/地标名称)
                    if (address.getFeatureName() != null && !address.getFeatureName().isEmpty()) {
                        sb.append(address.getFeatureName());
                    }
                    
                    // 添加 subThoroughfare (门牌号)
                    if (address.getSubThoroughfare() != null) {
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append(address.getSubThoroughfare());
                    }
                    
                    // 添加 thoroughfare (街道名称)
                    if (address.getThoroughfare() != null) {
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append(address.getThoroughfare());
                    }
                    
                    // 添加 subLocality (社区/街道办)
                    if (address.getSubLocality() != null) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(address.getSubLocality());
                    }
                    
                    // 添加 subAdminArea (区/县)
                    if (address.getSubAdminArea() != null) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(address.getSubAdminArea());
                    }
                    
                    // 添加 locality (城市)
                    if (address.getLocality() != null) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(address.getLocality());
                    }
                    
                    // 添加 adminArea (省/州)
                    if (address.getAdminArea() != null) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(address.getAdminArea());
                    }
                    
                    String detailedResult = sb.toString();
                    // 如果找到了包含街道或建筑物的详细地址，直接返回
                    if (!detailedResult.isEmpty() && 
                        (address.getThoroughfare() != null || address.getFeatureName() != null)) {
                        Log.d(TAG, "✅ Geocoder详细地址: " + detailedResult);
                        return detailedResult;
                    }
                }
                
                // 如果没有找到详细地址，使用第一个结果
                Address address = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                if (address.getSubAdminArea() != null) sb.append(address.getSubAdminArea());
                if (address.getLocality() != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(address.getLocality());
                }
                if (address.getAdminArea() != null) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(address.getAdminArea());
                }
                String basicResult = sb.toString();
                if (!basicResult.isEmpty()) {
                    Log.d(TAG, "✅ Geocoder基础地址: " + basicResult);
                    return basicResult;
                }
            } else {
                Log.d(TAG, "⚠️ Geocoder未返回任何结果");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Geocoder失败: " + e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public int getTimeout() {
        return TIMEOUT;
    }
    
    @Override
    public String getDescription() {
        return "Android原生Geocoder服务（依赖Google服务）";
    }
}