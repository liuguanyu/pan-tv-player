# 图片地点提取崩溃修复

## 修复日期
2026-02-04

## 问题描述

### 异常信息
```
获取图片地点失败: setDataSourceCallback failed: status = 0x80000000
java.lang.RuntimeException: setDataSourceCallback failed: status = 0x80000000
    at android.media.MediaMetadataRetriever._setDataSource(Native Method)
    at android.media.MediaMetadataRetriever.setDataSource(MediaMetadataRetriever.java:209)
    at android.media.ExifInterface.getHeifAttributes(ExifInterface.java:2528)
    at android.media.ExifInterface.loadAttributes(ExifInterface.java:1701)
    at android.media.ExifInterface.<init>(ExifInterface.java:1403)
    at com.baidu.tv.player.utils.LocationUtils.getLocationFromImage(LocationUtils.java:75)
    at com.baidu.tv.player.service.LocationExtractionService.handleExtraction(LocationExtractionService.java:65)
```

### 问题原因
1. **直接从网络流读取 EXIF 的兼容性问题**：
   - `ExifInterface` 在某些 Android 版本上对 `InputStream` 的支持有限
   - 特别是对于 HEIF/HEIC 格式的图片，`ExifInterface` 内部使用 `MediaMetadataRetriever` 来解析
   - `MediaMetadataRetriever` 在处理某些流时会抛出 `RuntimeException`

2. **异常捕获不够全面**：
   - 虽然有 try-catch 块，但某些 native 层的异常可能导致应用崩溃
   - 需要捕获 `Throwable` 而不仅仅是 `Exception`

## 修复方案

### 1. 改进图片 EXIF 读取方式

#### 修改文件：`LocationUtils.java`

**修改前**：
```java
// 直接从网络流读取 EXIF
InputStream inputStream = connection.getInputStream();
ExifInterface exif = new ExifInterface(inputStream);
```

**修改后**：
```java
// 先下载到临时文件，再从文件读取 EXIF
tempFile = File.createTempFile("location_exif_", ".tmp", context.getCacheDir());
FileOutputStream outputStream = new FileOutputStream(tempFile);

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

// 从临时文件读取 EXIF 信息
ExifInterface exif = new ExifInterface(tempFile.getAbsolutePath());
```

**优点**：
- 避免了直接从网络流读取的兼容性问题
- 支持更多图片格式（包括 HEIF/HEIC）
- 可以限制文件大小，避免下载过大的文件
- 更稳定，不容易崩溃

**缺点**：
- 需要额外的磁盘空间（临时文件）
- 需要先下载完整文件，速度稍慢
- 但对于地点提取这个场景，这些缺点是可以接受的

### 2. 增强异常捕获

#### 修改文件：`LocationUtils.java`

**修改前**：
```java
} catch (Exception e) {
    android.util.Log.e("LocationUtils", "获取图片地点失败: " + e.getMessage(), e);
}
```

**修改后**：
```java
} catch (Throwable e) {
    // 捕获所有异常，包括 RuntimeException 和 Error
    android.util.Log.e("LocationUtils", "获取图片地点失败: " + e.getMessage(), e);
}
```

#### 修改文件：`LocationExtractionService.java`

**修改前**：
```java
} catch (Exception e) {
    Log.e(TAG, "Error during extraction: " + e.getMessage(), e);
}
```

**修改后**：
```java
} catch (Throwable e) {
    // 捕获所有异常和错误，包括 RuntimeException 和 native 崩溃
    // 这样可以防止服务崩溃影响主应用
    Log.e(TAG, "Error during extraction: " + e.getMessage(), e);
    Log.e(TAG, "Exception type: " + e.getClass().getName());
    if (e instanceof RuntimeException) {
        Log.e(TAG, "⚠️ RuntimeException 在地点提取过程中发生");
    }
}
```

**优点**：
- 捕获所有可能的异常，包括 `RuntimeException` 和 `Error`
- 防止服务崩溃影响主应用
- 提供更详细的日志信息，便于调试

### 3. 资源清理

**修改文件：`LocationUtils.java`

添加了 `finally` 块来确保资源被正确清理：

```java
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
```

## 测试建议

### 1. 测试不同格式的图片
- JPEG 格式
- PNG 格式
- HEIF/HEIC 格式（如果设备支持）
- 其他常见图片格式

### 2. 测试不同大小的图片
- 小图片（< 1MB）
- 中等图片（1-5MB）
- 大图片（5-10MB）
- 超大图片（> 10MB，应该被跳过）

### 3. 测试网络异常情况
- 网络中断
- 服务器返回错误
- 超时情况

### 4. 日志监控
关注以下日志：
```
LocationUtils: 开始从图片获取地点: [URL]
LocationUtils: 图片请求响应码: 200
LocationUtils: 图片下载完成，大小: [bytes] bytes
LocationUtils: EXIF GPS坐标: [lat],[lon]
LocationUtils: 临时文件删除成功
```

如果出现错误：
```
LocationUtils: 获取图片地点失败: [error message]
LocationExtractionService: Error during extraction: [error message]
LocationExtractionService: Exception type: [exception class name]
```

## 已知问题

### 1. 临时文件占用空间
- 每次提取地点都会创建临时文件
- 虽然会自动删除，但在某些情况下可能残留
- 建议：定期清理缓存目录

### 2. 下载速度
- 需要先下载完整文件才能读取 EXIF
- 对于大文件可能需要较长时间
- 建议：可以考虑只下载文件头部分（但实现较复杂）

### 3. HEIF/HEIC 格式支持
- 某些旧设备可能不支持 HEIF/HEIC 格式
- `ExifInterface` 在这些设备上可能无法正确解析
- 建议：可以添加格式检测，对于不支持的格式提前跳过

## 后续优化建议

1. **添加格式检测**
   - 在下载前检测图片格式
   - 对于不支持的格式提前跳过
   - 避免不必要的下载

2. **优化下载策略**
   - 只下载文件头部分（前几KB）
   - 大多数 EXIF 信息都在文件头
   - 可以显著减少下载时间和流量

3. **添加缓存机制**
   - 对已提取的地点信息进行缓存
   - 避免重复提取
   - 提高性能

4. **改进错误提示**
   - 向用户显示更友好的错误信息
   - 区分不同类型的错误（网络错误、格式不支持等）

5. **添加进度反馈**
   - 显示下载进度
   - 提升用户体验

## 相关文件

- [`LocationUtils.java`](app/src/main/java/com/baidu/tv/player/utils/LocationUtils.java) - 地点提取工具类
- [`LocationExtractionService.java`](app/src/main/java/com/baidu/tv/player/service/LocationExtractionService.java) - 地点提取服务