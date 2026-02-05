package com.baidu.tv.player.geocoding;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * OpenStreetMap Nominatim逆地理编码策略
 * 优点：完全免费、无需API Key、国际通用
 * 缺点：速度较慢、有请求频率限制
 */
public class NominatimGeocodingStrategy implements GeocodingStrategy {
    
    private static final String TAG = "NominatimGeocodingStrategy";
    private static final String NOMINATIM_API_URL = "https://nominatim.openstreetmap.org/reverse";
    private static final int CONNECTION_TIMEOUT = 3000;  // 3秒
    private static final int READ_TIMEOUT = 5000;        // 5秒
    
    @Override
    public String getName() {
        return "Nominatim";
    }
    
    @Override
    public boolean isAvailable(Context context) {
        // Nominatim API始终可用（只要网络连接正常）
        return true;
    }
    
    @Override
    public int getPriority() {
        return 3; // 最低优先级（作为备用方案）
    }
    
    @Override
    public String getAddress(Context context, double latitude, double longitude) {
        try {
            Log.d(TAG, "开始Nominatim逆地理编码");
            
            // 构建请求URL
            String urlString = NOMINATIM_API_URL + String.format(Locale.US,
                    "?format=json&lat=%f&lon=%f&accept-language=zh",
                    latitude, longitude);
            
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
                
                // 尝试从 address 对象构建最详细的地址
                JSONObject address = json.optJSONObject("address");
                if (address != null) {
                    Log.d(TAG, "Address对象: " + address.toString());
                    
                    StringBuilder sb = new StringBuilder();
                    
                    // 1. 建筑物/POI
                    String building = "";
                    if (address.has("building")) building = address.optString("building");
                    else if (address.has("mall")) building = address.optString("mall");
                    else if (address.has("theatre")) building = address.optString("theatre");
                    else if (address.has("cinema")) building = address.optString("cinema");
                    else if (address.has("hotel")) building = address.optString("hotel");
                    else if (address.has("amenity")) building = address.optString("amenity");
                    else if (address.has("leisure")) building = address.optString("leisure");
                    else if (address.has("tourism")) building = address.optString("tourism");
                    else if (address.has("shop")) building = address.optString("shop");
                    else if (address.has("office")) building = address.optString("office");
                    
                    if (!building.isEmpty()) {
                        sb.append(building);
                    }
                    
                    // 2. 门牌号
                    String houseNumber = address.optString("house_number", "");
                    if (!houseNumber.isEmpty()) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(houseNumber);
                    }
                    
                    // 3. 街道
                    String road = address.optString("road", "");
                    if (road.isEmpty()) road = address.optString("pedestrian", "");
                    if (road.isEmpty()) road = address.optString("street", "");
                    
                    if (!road.isEmpty()) {
                        if (sb.length() > 0) sb.append(" ");
                        sb.append(road);
                    }
                    
                    // 4. 社区/小区/村庄
                    String neighborhood = address.optString("neighbourhood", "");
                    if (neighborhood.isEmpty()) neighborhood = address.optString("residential", "");
                    if (neighborhood.isEmpty()) neighborhood = address.optString("village", "");
                    if (neighborhood.isEmpty()) neighborhood = address.optString("hamlet", "");
                    
                    if (!neighborhood.isEmpty()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(neighborhood);
                    }
                    
                    // 5. 区/县 (Suburbs/Districts)
                    String district = address.optString("suburb", "");
                    if (district.isEmpty()) district = address.optString("district", "");
                    if (district.isEmpty()) district = address.optString("city_district", "");
                    if (district.isEmpty()) district = address.optString("borough", "");
                    if (district.isEmpty()) district = address.optString("county", "");
                    
                    if (!district.isEmpty()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(district);
                    }
                    
                    // 6. 城市
                    String city = address.optString("city", "");
                    if (city.isEmpty()) city = address.optString("town", "");
                    if (city.isEmpty()) city = address.optString("municipality", "");
                    
                    if (!city.isEmpty()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(city);
                    }
                    
                    // 7. 省/州
                    String state = address.optString("state", "");
                    if (state.isEmpty()) state = address.optString("province", "");
                    if (state.isEmpty()) state = address.optString("region", "");
                    
                    if (!state.isEmpty()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(state);
                    }
                    
                    String detailedAddress = sb.toString();
                    if (!detailedAddress.isEmpty()) {
                        Log.d(TAG, "✅ Nominatim详细地址: " + detailedAddress);
                        connection.disconnect();
                        return detailedAddress;
                    }
                }
                
                // 如果构建失败，回退到 display_name
                String displayName = json.optString("display_name", "");
                if (!displayName.isEmpty()) {
                    Log.d(TAG, "✅ Nominatim完整地址: " + displayName);
                    connection.disconnect();
                    return displayName;
                }
            } else {
                Log.w(TAG, "⚠️ HTTP错误: " + responseCode);
            }
            connection.disconnect();
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Nominatim API调用失败: " + e.getMessage());
        }
        
        return null;
    }
    
    @Override
    public int getTimeout() {
        return CONNECTION_TIMEOUT + READ_TIMEOUT;
    }
    
    @Override
    public String getDescription() {
        return "OpenStreetMap Nominatim服务（国际通用，免费）";
    }
}