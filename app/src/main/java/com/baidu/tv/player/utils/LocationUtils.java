
package com.baidu.tv.player.utils;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.baidu.tv.player.config.BaiduConfig;
import com.baidu.tv.player.geocoding.GeocodingFactory;

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
    
    // ==================== 缓存配置 ====================
    // L1: 内存缓存（快速访问）
    private static final ConcurrentHashMap<String, String> memoryCache = new ConcurrentHashMap<>();
    private static final int MAX_MEMORY_CACHE_SIZE = 1000; // 最多缓存1000个位置
    
    // L2: 本地持久化缓存（SharedPreferences）
    private static final String PREFS_NAME = "location_cache";
    private static final String CACHE_KEY_PREFIX = "loc_";
    private static final int MAX_DISK_CACHE_SIZE = 5000; // 最多缓存5000个位置
    private static final long CACHE_EXPIRY_DAYS = 30; // 缓存30天后过期
    
    // Nominatim API 基础URL（完全免费，不需要API Key）
    private static final String NOMINATIM_API_URL = "https://nominatim.openstreetmap.org/reverse";
    
    // 高德地图API配置（从配置文件读取）
    private static final String AMAP_API_KEY = BaiduConfig.AMAP_API_KEY;
    private static final String AMAP_API_URL = "https://restapi.amap.com/v3/geocode/regeo";
    
    // 请求超时时间（毫秒）- 缩短超时时间提高响应速度
    private static final int CONNECTION_TIMEOUT = 3000;  // 3秒连接超时
    private static final int READ_TIMEOUT = 5000;        // 5秒读取超时
    
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
     * 使用临时文件方式读取EXIF，避免直接从网络流读取的兼容性问题
     */
    public static String getLocationFromImage(Context context, String imageUrl) {
        File tempFile = null;
        InputStream inputStream = null;
        HttpURLConnection connection = null;
        
        try {
            Log.d(TAG, "开始从图片获取地点: " + imageUrl);
            
            // 从URL下载图片到临时文件
            URL url = new URL(imageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            // 设置百度网盘需要的User-Agent
            connection.setRequestProperty("User-Agent", "pan.baidu.com");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.connect();
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "图片请求响应码: " + responseCode);
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                inputStream = connection.getInputStream();
                
                // 创建临时文件
                tempFile = File.createTempFile("location_exif_", ".tmp", context.getCacheDir());
                java.io.FileOutputStream outputStream = new java.io.FileOutputStream(tempFile);
                
                // 下载图片到临时文件
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    
                    // 限制文件大小，避免下载过大的文件
                    if (totalBytes > 10 * 1024 * 1024) { // 10MB
                        Log.w(TAG, "图片文件过大，停止下载: " + totalBytes + " bytes");
                        break;
                    }
                }
                outputStream.flush();
                outputStream.close();
                
                Log.d(TAG, "图片下载完成，大小: " + totalBytes + " bytes");
                
                // 从临时文件读取EXIF信息
                ExifInterface exif = new ExifInterface(tempFile.getAbsolutePath());
                
                // 获取GPS坐标
                float[] latLong = new float[2];
                boolean hasLatLong = exif.getLatLong(latLong);
                Log.d(TAG, "EXIF GPS坐标: " + (hasLatLong ? latLong[0] + "," + latLong[1] : "null"));
                
                if (hasLatLong) {
                    double latitude = latLong[0];
                    double longitude = latLong[1];
                    return getLocationFromCoordinates(context, latitude, longitude);
                }
            }
        } catch (Throwable e) {
            // 捕获所有异常，包括 RuntimeException 和 Error
            android.util.Log.e("LocationUtils", "获取图片地点失败: " + e.getMessage(), e);
        } finally {
            // 清理资源
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                // ignore
            }
            
            try {
                if (connection != null) {
                    connection.disconnect();
                }
            } catch (Exception e) {
                // ignore
            }
            
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                Log.d(TAG, "临时文件删除" + (deleted ? "成功" : "失败"));
            }
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
        // 生成缓存Key（保留4位小数，约11米精度）
        String cacheKey = String.format(Locale.US, "%.4f,%.4f", latitude, longitude);
        
        // L1: 检查内存缓存
        String cachedLocation = memoryCache.get(cacheKey);
        if (cachedLocation != null) {
            Log.d(TAG, "GPS_DEBUG:💾 [L1命中] 内存缓存: " + cachedLocation);
            return cachedLocation;
        }
        
        // L2: 检查本地持久化缓存
        cachedLocation = loadFromDiskCache(context, cacheKey);
        if (cachedLocation != null) {
            Log.d(TAG, "GPS_DEBUG:💾 [L2命中] 本地缓存: " + cachedLocation);
            // 回填到内存缓存
            memoryCache.put(cacheKey, cachedLocation);
            return cachedLocation;
        }
        
        Log.d(TAG, "GPS_DEBUG:🔍 [缓存未命中] 需要调用API");
        
        // 使用策略模式获取地址
        String location = GeocodingFactory.getInstance().getAddress(context, latitude, longitude);
        if (location != null) {
            // 保存到双层缓存
            saveToCache(context, cacheKey, location);
            Log.d(TAG, "GPS_DEBUG:✅ 策略模式获取地址成功: " + location);
            return location;
        }
        
        // 如果所有地理编码方法都失败，不显示地点信息
        Log.d(TAG, "GPS_DEBUG:❌ 所有地理编码方法失败，返回null");
        return null; // 无法获取地点名称
    }

    /**
     * 异步获取地点信息的接口
     */
    public interface LocationCallback {
        void onLocationRetrieved(String location);
    }
    
    /**
     * 保存到双层缓存
     */
    private static void saveToCache(Context context, String cacheKey, String location) {
        // L1: 保存到内存缓存（LRU策略）
        if (memoryCache.size() >= MAX_MEMORY_CACHE_SIZE) {
            // 简单的LRU：移除第一个元素
            String firstKey = memoryCache.keySet().iterator().next();
            memoryCache.remove(firstKey);
            Log.d(TAG, "GPS_DEBUG:💾 [L1清理] 移除旧缓存: " + firstKey);
        }
        memoryCache.put(cacheKey, location);
        
        // L2: 保存到本地持久化缓存
        saveToDiskCache(context, cacheKey, location);
    }
    
    /**
     * 从本地缓存加载
     */
    private static String loadFromDiskCache(Context context, String cacheKey) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String fullKey = CACHE_KEY_PREFIX + cacheKey;
            
            if (prefs.contains(fullKey)) {
                // 检查是否过期
                long timestamp = prefs.getLong(fullKey + "_time", 0);
                long currentTime = System.currentTimeMillis();
                long expiryTime = CACHE_EXPIRY_DAYS * 24 * 60 * 60 * 1000L;
                
                if (currentTime - timestamp > expiryTime) {
                    Log.d(TAG, "GPS_DEBUG:💾 [L2过期] 缓存已过期: " + cacheKey);
                    prefs.edit().remove(fullKey).remove(fullKey + "_time").apply();
                    return null;
                }
                
                String location = prefs.getString(fullKey, null);
                if (location != null) {
                    Log.d(TAG, "GPS_DEBUG:💾 [L2加载] 从本地缓存加载: " + cacheKey);
                    return location;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "GPS_DEBUG:❌ [L2错误] 加载本地缓存失败: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * 保存到本地缓存
     */
    private static void saveToDiskCache(Context context, String cacheKey, String location) {
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String fullKey = CACHE_KEY_PREFIX + cacheKey;
            
            // 检查缓存大小，如果超过限制则清理旧缓存
            int currentSize = prefs.getAll().size() / 2; // 每个缓存项占用2个key（数据+时间戳）
            if (currentSize >= MAX_DISK_CACHE_SIZE) {
                cleanupOldDiskCache(prefs);
            }
            
            // 保存缓存数据和时间戳
            prefs.edit()
                .putString(fullKey, location)
                .putLong(fullKey + "_time", System.currentTimeMillis())
                .apply();
            
            Log.d(TAG, "GPS_DEBUG:💾 [L2保存] 保存到本地缓存: " + cacheKey + " -> " + location);
        } catch (Exception e) {
            Log.e(TAG, "GPS_DEBUG:❌ [L2错误] 保存本地缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 清理旧的本地缓存（LRU策略）
     */
    private static void cleanupOldDiskCache(android.content.SharedPreferences prefs) {
        try {
            java.util.Map<String, ?> all = prefs.getAll();
            java.util.List<java.util.Map.Entry<String, Long>> entries = new java.util.ArrayList<>();
            
            // 收集所有缓存项的时间戳
            for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("_time")) {
                    String dataKey = key.substring(0, key.length() - 5); // 移除"_time"后缀
                    if (dataKey.startsWith(CACHE_KEY_PREFIX)) {
                        Long timestamp = (Long) entry.getValue();
                        entries.add(new java.util.AbstractMap.SimpleEntry<>(dataKey, timestamp));
                    }
                }
            }
            
            // 按时间戳排序（最旧的在前）
            entries.sort(java.util.Comparator.comparingLong(java.util.Map.Entry::getValue));
            
            // 删除最旧的20%缓存
            int toRemove = Math.max(1, entries.size() / 5);
            for (int i = 0; i < toRemove; i++) {
                String key = entries.get(i).getKey();
                prefs.edit().remove(key).remove(key + "_time").apply();
                Log.d(TAG, "GPS_DEBUG:💾 [L2清理] 移除旧缓存: " + key);
            }
            
            Log.d(TAG, "GPS_DEBUG:💾 [L2清理] 清理完成，移除了 " + toRemove + " 个旧缓存项");
        } catch (Exception e) {
            Log.e(TAG, "GPS_DEBUG:❌ [L2错误] 清理本地缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 清空所有缓存
     */
    public static void clearAllCache(Context context) {
        // 清空内存缓存
        memoryCache.clear();
        Log.d(TAG, "GPS_DEBUG:💾 [清理] 内存缓存已清空");
        
        // 清空本地缓存
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
            Log.d(TAG, "GPS_DEBUG:💾 [清理] 本地缓存已清空");
        } catch (Exception e) {
            Log.e(TAG, "GPS_DEBUG:❌ [清理] 清空本地缓存失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public static String getCacheStats(Context context) {
        int memorySize = memoryCache.size();
        int diskSize = 0;
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            diskSize = prefs.getAll().size() / 2; // 每个缓存项占用2个key
        } catch (Exception e) {
            // ignore
        }
        return String.format(Locale.US, "内存缓存: %d/%d, 本地缓存: %d/%d",
            memorySize, MAX_MEMORY_CACHE_SIZE, diskSize, MAX_DISK_CACHE_SIZE);
    }
    
    /**
     * 释放资源（保留兼容性）
     */
    public static void release() {
        memoryCache.clear();
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
        
        // 方法1: 文本搜索（优先，因为更快）
        String location = searchForGPSCoordinates(context, data);
        if (location != null) {
            return location;
        }
        
        // 不再使用二进制数据搜索，因为其可靠性较低
        Log.d(TAG, GPS_DEBUG + "❌ 文本搜索失败，跳过不可靠的二进制数据搜索");
        
        return null;
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
                    // 只打印关键字本身，避免打印二进制乱码
                    Log.d(TAG, GPS_DEBUG + "🔍 找到关键字 '" + keyword + "' 在位置: " + index);
                    
                    // 尝试提取关键字附近的可打印ASCII字符
                    String nearbyText = extractPrintableText(dataString, index, 200);
                    if (!nearbyText.isEmpty()) {
                        Log.d(TAG, GPS_DEBUG + "🔍 关键字附近可打印文本: " + nearbyText);
                    }
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
                "©xyz.+?([+-]\\d+\\.\\d+)([+-]\\d+\\.\\d+)",
                
                // 增加更多的模糊匹配模式，应对二进制数据中的非标准格式
                // 匹配连续的两个浮点数，中间可能有乱码
                "([+-]\\d{2,3}\\.\\d{4,})[^\\d+-]{1,10}([+-]\\d{2,3}\\.\\d{4,})"
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
                    } else if (matcher.groupCount() >= 2) {
                        // 通用处理：如果匹配了两个组，假设是经纬度
                        String lat = matcher.group(1);
                        String lon = matcher.group(2);
                        // 清理非数字字符
                        lat = lat.replaceAll("[^\\d.+\\-]", "");
                        lon = lon.replaceAll("[^\\d.+\\-]", "");
                        match = "+" + lat + "+" + lon;
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
    
    
    /**
     * 提取字符串中附近的可打印文本（用于调试）
     */
    private static String extractPrintableText(String data, int center, int radius) {
        int start = Math.max(0, center - radius);
        int end = Math.min(data.length(), center + radius);
        StringBuilder sb = new StringBuilder();
        
        for (int i = start; i < end; i++) {
            char c = data.charAt(i);
            // 只保留可打印ASCII字符
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else {
                sb.append('.'); // 不可打印字符用点代替
            }
        }
        return sb.toString();
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

    // ====================== 核心算法：WGS84 转 GCJ02 (火星坐标) ======================
    // 参考：https://github.com/googollee/eviltransform
    private static final double PI = 3.1415926535897932384626;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    public static double[] WGS84ToGCJ02(double lon, double lat) {
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

    private static boolean outOfChina(double lon, double lat) {
        return (lon < 72.004 || lon > 137.8347) || (lat < 0.8293 || lat > 55.8271);
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }
}