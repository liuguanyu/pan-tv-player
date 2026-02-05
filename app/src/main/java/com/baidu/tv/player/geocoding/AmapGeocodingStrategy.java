package com.baidu.tv.player.geocoding;

import android.content.Context;
import android.util.Log;

import com.baidu.tv.player.config.BaiduConfig;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * 高德地图逆地理编码策略
 * 优点：在中国境内速度快、准确度高、数据详细
 * 缺点：需要API Key、有请求限制
 */
public class AmapGeocodingStrategy implements GeocodingStrategy {
    
    private static final String TAG = "AmapGeocodingStrategy";
    private static final String AMAP_API_URL = "https://restapi.amap.com/v3/geocode/regeo";
    private static final int CONNECTION_TIMEOUT = 3000;  // 3秒
    private static final int READ_TIMEOUT = 5000;        // 5秒
    
    // WGS84转GCJ02坐标转换常量
    private static final double PI = 3.1415926535897932384626;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;
    
    @Override
    public String getName() {
        return "AMap";
    }
    
    @Override
    public boolean isAvailable(Context context) {
        // 检查API Key是否配置
        String apiKey = BaiduConfig.AMAP_API_KEY;
        return apiKey != null && !apiKey.isEmpty();
    }
    
    @Override
    public int getPriority() {
        return 1; // 最高优先级（中国境内）
    }
    
    @Override
    public String getAddress(Context context, double latitude, double longitude) {
        String apiKey = BaiduConfig.AMAP_API_KEY;
        if (apiKey == null || apiKey.isEmpty()) {
            Log.d(TAG, "API Key未配置");
            return null;
        }
        
        try {
            Log.d(TAG, "开始高德地图逆地理编码");
            
            // 坐标转换：WGS84 -> GCJ02（火星坐标系）
            double[] gcj = wgs84ToGcj02(longitude, latitude);
            double gcjLon = gcj[0];
            double gcjLat = gcj[1];
            
            Log.d(TAG, String.format(Locale.US, "坐标转换: WGS84(%.6f, %.6f) -> GCJ02(%.6f, %.6f)", 
                    longitude, latitude, gcjLon, gcjLat));
            
            // 构建请求URL（注意：高德API经度在前）
            String urlString = AMAP_API_URL + String.format(Locale.US,
                    "?key=%s&location=%f,%f&output=json&extensions=base",
                    apiKey, gcjLon, gcjLat);
            
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "BaiduTVPlayer/1.0");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // 解析JSON响应
                JSONObject json = new JSONObject(response.toString());
                String status = json.optString("status", "0");
                
                if ("1".equals(status)) {
                    JSONObject regeocode = json.optJSONObject("regeocode");
                    if (regeocode != null) {
                        String formattedAddress = regeocode.optString("formatted_address", "");
                        if (!formattedAddress.isEmpty()) {
                            Log.d(TAG, "✅ 高德地图地址: " + formattedAddress);
                            connection.disconnect();
                            return formattedAddress;
                        }
                    }
                } else {
                    String info = json.optString("info", "未知错误");
                    Log.w(TAG, "⚠️ 高德地图API返回错误: " + status + " - " + info);
                }
            } else {
                Log.w(TAG, "⚠️ HTTP错误: " + responseCode);
            }
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ 高德地图API调用失败: " + e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public int getTimeout() {
        return CONNECTION_TIMEOUT + READ_TIMEOUT;
    }
    
    @Override
    public String getDescription() {
        return "高德地图逆地理编码服务（中国境内推荐）";
    }
    
    /**
     * WGS84坐标转GCJ02坐标（火星坐标系）
     * @param lon 经度
     * @param lat 纬度
     * @return [经度, 纬度]
     */
    private double[] wgs84ToGcj02(double lon, double lat) {
        if (outOfChina(lon, lat)) {
            return new double[]{lon, lat};
        }
        
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        double mgLat = lat + dLat;
        double mgLon = lon + dLon;
        return new double[]{mgLon, mgLat};
    }
    
    private boolean outOfChina(double lon, double lat) {
        return (lon < 72.004 || lon > 137.8347) || (lat < 0.8293 || lat > 55.8271);
    }
    
    private double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }
    
    private double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}