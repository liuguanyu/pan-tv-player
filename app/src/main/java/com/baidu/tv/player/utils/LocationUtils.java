
package com.baidu.tv.player.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地点识别工具类
 * 使用Android原生Geocoder API + OpenStreetMap Nominatim API作为备用
 */
public class LocationUtils {
    private static final String TAG = "LocationUtils";
    // 统一的GPS调试日志前缀，方便过滤
    private static final String GPS_DEBUG = "GPS_DEBUG:";
    
    // 位置缓存，避免重复请求
    private static final ConcurrentHashMap<String, String> locationCache = new ConcurrentHashMap<>();
    
    // Nominatim API 基础URL
    private static final String NOMINATIM_API_URL = "https://nominatim.openstreetmap.org/reverse";
    
    // 请求超时时间（毫秒）
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 10000;
    
    // 启用测试模式（用于调试）
    private static final boolean ENABLE_TEST_MODE = false;
    // 测试坐标：北京天安门
    private static final double TEST_LATITUDE = 39.9042;
    private static final double TEST_LONGITUDE = 116.4074;
    
    /**
     * 测试反向地理编码功能（用于调试）
     */
    
    /**
     * 从图片中获取地点信息
     */
    public static String getLocationFromImage(Context context, String imageUrl) {
        try {
            Log.d(TAG, "开始从图片获取地点: " + imageUrl);
            // 从URL下载图片并读取EXIF信息
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            // 设置百度网盘需要的User-Agent
            connection.setRequestProperty("User-Agent", "pan.baidu.com");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "图片请求响应码: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                ExifInterface exif = new ExifInterface(inputStream);
                
                // 获取GPS坐标
                float[] latLong = new float[2];
                boolean hasLatLong = exif.getLatLong(latLong);
                Log.d(TAG, "EXIF GPS坐标: " + (hasLatLong ? latLong[0] + "," + latLong[1] : "null"));
                
                if (hasLatLong) {
                    double latitude = latLong[0];
                    double longitude = latLong[1];
                    
                    inputStream.close();
                    connection.disconnect();
                    return getLocationFromCoordinates(context, latitude, longitude);
                }
                
                inputStream.close();
            }
            connection.disconnect();
        } catch (Exception e) {
            android.util.Log.e("LocationUtils", "获取图片地点失败: " + e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * 从视频中获取地点信息
     * 支持多种视频元数据格式
     */
    public static String getLocationFromVideo(Context context, String videoUrl) {
        Log.d(TAG, GPS_DEBUG + "========== 开始并行提取视频GPS信息 ==========");
        Log.d(TAG, GPS_DEBUG + "视频URL: " + videoUrl);
        
        // 使用并行执行多种提取策略，一旦有一种成功就停止其他任务
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(3);
        java.util.List<java.util.concurrent.Callable<String>> tasks = new java.util.ArrayList<>();
        
        // 任务1: 使用MediaMetadataRetriever（支持部分标准MP4元数据）
        tasks.add(() -> {
            Log.d(TAG, GPS_DEBUG + "[任务1] 开始使用MediaMetadataRetriever提取元数据");
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                // 注意：对于网络视频，setDataSource可能会阻塞，且如果不需要完整下载
                // 最好使用本地代理或只下载文件头的方式。这里先尝试直接设置URL
                // 百度网盘链接可能需要Headers
                java.util.HashMap<String, String> headers = new java.util.HashMap<>();
                headers.put("User-Agent", "pan.baidu.com");
                retriever.setDataSource(videoUrl, headers);
                
                String locationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
                Log.d(TAG, GPS_DEBUG + "[任务1] METADATA_KEY_LOCATION: " + locationString);
                
                if (locationString != null) {
                    // ISO-6709 格式: +37.7749-122.4194/
                    // 解析这个字符串
                    Log.d(TAG, GPS_DEBUG + "[任务1] ✅ 成功: 从MediaMetadataRetriever获得位置字符串: " + locationString);
                    String location = parseLocationString(context, locationString);
                    if (location != null) {
                        return location;
                    }
                } else {
                    Log.d(TAG, GPS_DEBUG + "[任务1] 未找到标准位置元数据");
                }
                
                // 打印其他元数据帮助调试
                String date = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
                String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                Log.d(TAG, GPS_DEBUG + "[任务1] METADATA_KEY_DATE: " + date);
                Log.d(TAG, GPS_DEBUG + "[任务1] METADATA_KEY_VIDEO_ROTATION: " + rotation);
                
            } catch (Exception e) {
                Log.e(TAG, GPS_DEBUG + "[任务1] MediaMetadataRetriever提取失败: " + e.getMessage());
            } finally {
                try {
                    retriever.release();
                } catch (Exception e) {
                    // ignore
                }
            }
            
            // 尝试解析 ISO6709 格式的字符串，这在Apple设备中很常见
            // Apple设备通常将位置存储为 "+22.5430+114.0578+000.000/" 格式
            
            // 如果上述方法都失败了，我们再尝试更激进的方法：
            // 尝试读取文件的特定字节（但这在流媒体中很难实现）
            
            // 方法4: 尝试从视频文件的EXIF数据中提取（如果视频包含嵌入的EXIF）
            // 对于流媒体，尝试下载文件头部分来解析元数据
            throw new Exception("MediaMetadataRetriever未找到GPS信息");
        });
        
        // 任务2: 从视频文件头提取GPS信息
        tasks.add(() -> {
            if (videoUrl != null && videoUrl.startsWith("http")) {
                Log.d(TAG, GPS_DEBUG + "[任务2] 开始从文件头提取GPS信息");
                String locationFromHeader = getLocationFromVideoHeader(context, videoUrl);
                if (locationFromHeader != null) {
                    Log.d(TAG, GPS_DEBUG + "[任务2] ✅ 成功: 从文件头解析到位置: " + locationFromHeader);
                    return locationFromHeader;
                } else {
                    Log.d(TAG, GPS_DEBUG + "[任务2] 文件头中未找到GPS信息");
                }
            } else {
                Log.d(TAG, GPS_DEBUG + "[任务2] 跳过文件头提取（非HTTP URL）");
            }
            throw new Exception("文件头提取未找到GPS信息");
        });
        
        // 任务3: 从视频文件尾部提取GPS信息
        tasks.add(() -> {
            if (videoUrl != null && videoUrl.startsWith("http")) {
                Log.d(TAG, GPS_DEBUG + "[任务3] 开始从文件尾部提取GPS信息");
                String locationFromTail = getLocationFromVideoTail(context, videoUrl);
                if (locationFromTail != null) {
                    Log.d(TAG, GPS_DEBUG + "[任务3] ✅ 成功: 从文件尾部解析到位置: " + locationFromTail);
                    return locationFromTail;
                } else {
                    Log.d(TAG, GPS_DEBUG + "[任务3] 文件尾部中未找到GPS信息");
                }
            } else {
                Log.d(TAG, GPS_DEBUG + "[任务3] 跳过文件尾部提取（非HTTP URL）");
            }
            throw new Exception("文件尾部提取未找到GPS信息");
        });
        
        try {
            // invokeAny会在第一个任务成功返回时取消其他任务
            String result = executor.invokeAny(tasks);
            Log.d(TAG, GPS_DEBUG + "✅ 并行提取成功，结果: " + result);
            executor.shutdown();
            return result;
        } catch (java.util.concurrent.ExecutionException e) {
            Log.d(TAG, GPS_DEBUG + "❌ 所有并行提取任务均失败: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            Log.d(TAG, GPS_DEBUG + "❌ 并行提取被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdownNow();
        }
        
        Log.d(TAG, GPS_DEBUG + "❌ 失败: 所有并行方法均未找到GPS信息");
        Log.d(TAG, GPS_DEBUG + "========== GPS提取结束 ==========");
        return null;
    }
    
    /**
     * 解析位置字符串 (ISO-6709 标准)
     * 格式如: +37.7749-122.4194/ 或 +37.7749-122.4194
     */
    private static String parseLocationString(Context context, String locationString) {
        if (locationString == null) return null;
        
        try {
            // 清理字符串，移除结尾的/
            if (locationString.endsWith("/")) {
                locationString = locationString.substring(0, locationString.length() - 1);
            }
            
            // 使用正则解析
            // 匹配格式: ([+-]DD.DDDD)([+-]DDD.DDDD)
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(locationString);
            
            if (matcher.find()) {
                double lat = Double.parseDouble(matcher.group(1));
                double lon = Double.parseDouble(matcher.group(2));
                
                Log.d(TAG, GPS_DEBUG + "解析到坐标: " + lat + ", " + lon);
                return getLocationFromCoordinates(context, lat, lon);
            }
        } catch (Exception e) {
            Log.e(TAG, GPS_DEBUG + "解析位置字符串失败: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 根据经纬度获取地点名称
     */
    public static String getLocationFromCoordinates(Context context, double latitude, double longitude) {
        // 检查缓存
        String cacheKey = String.format(Locale.US, "%.4f,%.4f", latitude, longitude);
        if (locationCache.containsKey(cacheKey)) {
            return locationCache.get(cacheKey);
        }
        
        // 1. 尝试使用Android原生Geocoder (使用中文)
        try {
            Geocoder geocoder = new Geocoder(context, Locale.CHINESE);
            // 获取多个结果以找到最详细的地址
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 5);
            
            if (addresses != null && !addresses.isEmpty()) {
                Log.d(TAG, "GPS_DEBUG:📋 Geocoder返回了 " + addresses.size() + " 个结果");
                
                // 遍历所有结果，找到最详细的地址
                for (int i = 0; i < addresses.size(); i++) {
                    Address address = addresses.get(i);
                    
                    // 调试：打印每个地址的所有可用信息
                    Log.d(TAG, "GPS_DEBUG:📋 地址[" + i + "]信息:");
                    Log.d(TAG, "  - featureName: " + address.getFeatureName());
                    Log.d(TAG, "  - premises: " + address.getPremises());
                    Log.d(TAG, "  - subThoroughfare: " + address.getSubThoroughfare());
                    Log.d(TAG, "  - thoroughfare: " + address.getThoroughfare());
                    Log.d(TAG, "  - subLocality: " + address.getSubLocality());
                    Log.d(TAG, "  - locality: " + address.getLocality());
                    Log.d(TAG, "  - subAdminArea: " + address.getSubAdminArea());
                    Log.d(TAG, "  - adminArea: " + address.getAdminArea());
                    Log.d(TAG, "  - postalCode: " + address.getPostalCode());
                    Log.d(TAG, "  - countryName: " + address.getCountryName());
                    
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
                    
                    String result = sb.toString();
                    // 如果找到了包含街道或建筑物的详细地址，直接返回
                    if (!result.isEmpty() && (address.getThoroughfare() != null || address.getFeatureName() != null)) {
                        locationCache.put(cacheKey, result);
                        Log.d(TAG, "GPS_DEBUG:✅ Geocoder详细地址[" + i + "]: " + result);
                        return result;
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
                String result = sb.toString();
                if (!result.isEmpty()) {
                    locationCache.put(cacheKey, result);
                    Log.d(TAG, "GPS_DEBUG:✅ Geocoder基础地址: " + result);
                    return result;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder failed: " + e.getMessage());
        }
        
        // 2. 如果原生Geocoder失败，使用OpenStreetMap Nominatim API
        try {
            String urlString = NOMINATIM_API_URL + String.format(Locale.US, "?format=json&lat=%f&lon=%f&accept-language=zh",
                    latitude, longitude);
            
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "BaiduTVPlayer/1.0");
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject json = new JSONObject(response.toString());
                
                // 调试：打印完整的JSON响应
                Log.d(TAG, "GPS_DEBUG:📋 Nominatim原始响应: " + json.toString());
                
                // 尝试从 address 对象构建最详细的地址
                // Nominatim 返回的 address 对象结构很丰富
                JSONObject address = json.optJSONObject("address");
                if (address != null) {
                    Log.d(TAG, "GPS_DEBUG:📋 Address对象: " + address.toString());
                    
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
                        locationCache.put(cacheKey, detailedAddress);
                        Log.d(TAG, "GPS_DEBUG:✅ Nominatim详细地址: " + detailedAddress);
                        return detailedAddress;
                    }
                }

                // 如果构建失败，回退到 display_name
                String displayName = json.optString("display_name", "");
                if (!displayName.isEmpty()) {
                    locationCache.put(cacheKey, displayName);
                    Log.d(TAG, "GPS_DEBUG:✅ Nominatim完整地址: " + displayName);
                    return displayName;
                }
            }
            connection.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "Nominatim API failed: " + e.getMessage());
        }
        
        return null; // 无法获取地点名称
    }

    /**
     * 异步获取地点信息的接口
     */
    public interface LocationCallback {
        void onLocationRetrieved(String location);
    }
    
    /**
     * 释放资源
     */
    public static void release() {
        locationCache.clear();
    }
    
    /**
     * 异步获取图片地点信息
     */
    public static void getLocationFromImageAsync(Context context, String imageUrl, LocationCallback callback) {
        new Thread(() -> {
            String location = getLocationFromImage(context, imageUrl);
            if (callback != null) {
                callback.onLocationRetrieved(location);
            }
        }).start();
    }
    
    /**
     * 异步获取视频地点信息
     */
    public static void getLocationFromVideoAsync(Context context, String videoUrl, LocationCallback callback) {
        new Thread(() -> {
            String location = getLocationFromVideo(context, videoUrl);
            if (callback != null) {
                callback.onLocationRetrieved(location);
            }
        }).start();
    }
    
    /**
     * 从视频文件头/尾提取GPS信息
     */
    private static String getLocationFromVideoHeader(Context context, String videoUrl) {
        Log.d(TAG, GPS_DEBUG + "开始从视频文件头提取GPS信息: " + videoUrl);
        
        // 下载文件头部分（增加到2MB以确保覆盖更多元数据，特别是moov可能在文件末尾的情况）
        int downloadSize = 2 * 1024 * 1024;
        byte[] headerData = downloadVideoHeader(videoUrl, downloadSize);
        
        if (headerData == null || headerData.length == 0) {
            Log.d(TAG, GPS_DEBUG + "❌ 下载视频文件头失败或为空");
            return null;
        }
        
        Log.d(TAG, GPS_DEBUG + "✅ 成功下载视频文件头，大小: " + headerData.length + " bytes");
        
        return processVideoData(context, headerData);
    }
    
    /**
     * 从视频文件尾部提取GPS信息
     */
    private static String getLocationFromVideoTail(Context context, String videoUrl) {
        Log.d(TAG, GPS_DEBUG + "开始从视频文件尾部提取GPS信息: " + videoUrl);
        
        // 下载文件尾部2MB
        int downloadSize = 2 * 1024 * 1024;
        byte[] tailData = downloadVideoTail(videoUrl, downloadSize);
        
        if (tailData == null || tailData.length == 0) {
            Log.d(TAG, GPS_DEBUG + "❌ 下载视频文件尾部失败或为空");
            return null;
        }
        
        Log.d(TAG, GPS_DEBUG + "✅ 成功下载视频文件尾部，大小: " + tailData.length + " bytes");
        
        return processVideoData(context, tailData);
    }
    
    /**
     * 处理视频数据（头或尾），尝试提取GPS
     */
    private static String processVideoData(Context context, byte[] data) {
        // 直接使用文本搜索，移除对mp4parser的依赖
        // 实践证明，简单的文本搜索对于提取ISO-6709格式的GPS信息非常有效
        // 且不需要创建临时文件和引入复杂的解析逻辑
        Log.d(TAG, GPS_DEBUG + "开始在视频数据中搜索GPS信息...");
        return searchForGPSCoordinates(context, data);
    }

    /**
     * 在二进制数据中搜索文本格式的GPS坐标
     */
    private static String searchForGPSCoordinates(Context context, byte[] data) {
        try {
            // 将字节数据转换为字符串，搜索GPS坐标
            // 使用ISO-8859-1编码，因为它可以无损地表示所有字节值
            String dataString = new String(data, "ISO-8859-1");
            Log.d(TAG, GPS_DEBUG + "数据转换为字符串长度: " + dataString.length());
            
            // 搜索可能包含GPS信息的关键字，帮助诊断
            String[] gpsKeywords = {"ISO6709", "location", "Location", "LOCATION", "GPS", "gps", "coordinates", "Coordinates", "xyz", "©xyz"};
            for (String keyword : gpsKeywords) {
                if (dataString.contains(keyword)) {
                    int index = dataString.indexOf(keyword);
                    int start = Math.max(0, index - 50);
                    int end = Math.min(dataString.length(), index + 100);
                    Log.d(TAG, GPS_DEBUG + "🔍 找到关键字 '" + keyword + "' 附近内容: " + dataString.substring(start, end));
                }
            }
            
            // 增加更多的正则表达式模式以覆盖不同厂商的格式
            String[] patterns = {
                // ISO 6709 标准格式
                "[+-]\\d{2,3}\\.\\d{4,}[+-]\\d{2,3}\\.\\d{4,}",     // +22.5430+114.0578
                "[+-]\\d{2,3}\\.\\d{4,}[+-]\\d{2,3}\\.\\d{4,}/",    // +22.5430+114.0578/
                
                // 带空格的格式
                "[+-]\\d{2,3}\\.\\d{4,}\\s+[+-]\\d{2,3}\\.\\d{4,}", // +22.5430 +114.0578
                
                // 度分秒格式 (简单的近似匹配)
                "\\d{1,3}deg\\s*\\d{1,2}'\\s*\\d{1,2}\\.?\\d*\"[NS]\\s*,?\\s*\\d{1,3}deg\\s*\\d{1,2}'\\s*\\d{1,2}\\.?\\d*\"[EW]",
                
                // Apple QuickTime 格式常见变体
                "([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)?/?",
                
                // 常见的JSON格式中的坐标
                "\"latitude\":\\s*([+-]?\\d+\\.\\d+).*\"longitude\":\\s*([+-]?\\d+\\.\\d+)",
                
                // XYZ原子内容格式
                "©xyz.+?([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)"
            };
            
            Log.d(TAG, GPS_DEBUG + "开始使用 " + patterns.length + " 种正则模式搜索GPS坐标");
            
            for (String patternStr : patterns) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);
                java.util.regex.Matcher matcher = pattern.matcher(dataString);
                
                if (matcher.find()) {
                    String match = matcher.group();
                    Log.d(TAG, GPS_DEBUG + "✅ 正则匹配成功 (" + patternStr + "): " + match);
                    
                    // 特殊处理JSON格式，需要提取组
                    if (patternStr.contains("latitude")) {
                        if (matcher.groupCount() >= 2) {
                            String lat = matcher.group(1);
                            String lon = matcher.group(2);
                            match = "+" + lat + "+" + lon; // 构造标准格式以便解析
                        }
                    } else if (patternStr.contains("©xyz")) {
                         if (matcher.groupCount() >= 2) {
                            String lat = matcher.group(1);
                            String lon = matcher.group(2);
                            match = lat + lon;
                        }
                    }
                    
                    // 解析GPS坐标
                    String location = parseLocationString(context, match);
                    if (location != null) {
                        Log.d(TAG, GPS_DEBUG + "✅ 成功从数据获得位置: " + location);
                        return location;
                    } else {
                        Log.d(TAG, GPS_DEBUG + "⚠️ 找到坐标字符串但解析失败");
                    }
                }
            }
            
            // 如果正则表达式没有找到，尝试更直接的方法
            // 搜索类似 "+22.5430+114.0578" 的模式
            Log.d(TAG, GPS_DEBUG + "尝试直接搜索坐标模式...");
            java.util.regex.Pattern directPattern = java.util.regex.Pattern.compile("[+-]\\d+\\.\\d+[+-]\\d+\\.\\d+");
            java.util.regex.Matcher directMatcher = directPattern.matcher(dataString);
            
            if (directMatcher.find()) {
                String gpsString = directMatcher.group();
                Log.d(TAG, GPS_DEBUG + "✅ 直接搜索找到坐标字符串: " + gpsString);
                
                // 解析GPS坐标
                String location = parseLocationString(context, gpsString);
                if (location != null) {
                    Log.d(TAG, GPS_DEBUG + "✅ 成功解析直接搜索的坐标: " + location);
                    return location;
                } else {
                    Log.d(TAG, GPS_DEBUG + "⚠️ 直接搜索找到坐标但解析失败");
                }
            }
            
            Log.d(TAG, GPS_DEBUG + "❌ 未在数据中找到任何已知格式的GPS坐标");
            
        } catch (Exception e) {
            Log.e(TAG, GPS_DEBUG + "文本搜索失败: " + e.getMessage());
        }
        
        return null;
    }

    
    private static byte[] downloadVideoHeader(String videoUrl, int maxSize) {
        Log.d(TAG, GPS_DEBUG + "准备下载文件头，目标大小: " + maxSize + " bytes");
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(videoUrl);
            connection = (HttpURLConnection) url.openConnection();
            
            // 设置请求头
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "pan.baidu.com");
            connection.setConnectTimeout(5000); // 缩短超时时间到5秒，避免长时间阻塞
            connection.setReadTimeout(5000);
            
            // 首先尝试使用Range请求
            String rangeHeader = "bytes=0-" + (maxSize - 1);
            connection.setRequestProperty("Range", rangeHeader);
            Log.d(TAG, GPS_DEBUG + "发送Range请求: " + rangeHeader);
            
            // 检查响应码
            int responseCode = connection.getResponseCode();
            Log.d(TAG, GPS_DEBUG + "服务器响应码: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                Log.d(TAG, GPS_DEBUG + "服务器支持Range请求");
                return readInputStream(connection.getInputStream(), maxSize);
            } else if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, GPS_DEBUG + "服务器不支持Range请求，但返回了完整文件，尝试读取前" + maxSize + "字节");
                return readInputStream(connection.getInputStream(), maxSize);
            } else {
                Log.d(TAG, GPS_DEBUG + "服务器返回错误: " + responseCode);
            }
        } catch (Exception e) {
            Log.e(TAG, GPS_DEBUG + "下载文件头失败: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        
        return null;
    }
    
    private static byte[] downloadVideoTail(String videoUrl, int tailSize) {
        Log.d(TAG, GPS_DEBUG + "准备下载文件尾部，目标大小: " + tailSize + " bytes");
        HttpURLConnection connection = null;
        
        try {
            URL url = new URL(videoUrl);
            connection = (HttpURLConnection) url.openConnection();
            
            // 首先需要获取文件总大小
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("User-Agent", "pan.baidu.com");
            connection.setConnectTimeout(5000);
            
            int contentLength = connection.getContentLength();
            Log.d(TAG, GPS_DEBUG + "文件总大小: " + contentLength);
            connection.disconnect();
            
            if (contentLength <= 0) {
                Log.d(TAG, GPS_DEBUG + "无法获取文件总大小，无法定位尾部");
                return null;
            }
            
            if (contentLength <= tailSize) {
                Log.d(TAG, GPS_DEBUG + "文件较小，直接下载完整文件");
                return downloadVideoHeader(videoUrl, tailSize);
            }

            // 重新建立连接下载尾部
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "pan.baidu.com");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            String range = "bytes=" + (contentLength - tailSize) + "-" + (contentLength - 1);
            connection.setRequestProperty("Range", range);
            Log.d(TAG, GPS_DEBUG + "请求Range: " + range);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                Log.d(TAG, GPS_DEBUG + "服务器支持Range请求，下载尾部成功");
                return readInputStream(connection.getInputStream(), tailSize);
            } else {
                Log.d(TAG, GPS_DEBUG + "服务器不支持Range请求尾部 (Code: " + responseCode + ")");
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, GPS_DEBUG + "下载文件尾部失败: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }

    private static byte[] readInputStream(java.io.InputStream inputStream, int maxSize) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        int totalRead = 0;

        while (totalRead < maxSize && (nRead = inputStream.read(data, 0, Math.min(data.length, maxSize - totalRead))) != -1) {
            buffer.write(data, 0, nRead);
            totalRead += nRead;
        }
        buffer.flush();
        return buffer.toByteArray();
    }
}