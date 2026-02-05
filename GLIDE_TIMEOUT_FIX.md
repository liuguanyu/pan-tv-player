# Glide 图片加载超时修复

## 问题描述

在长按图片文件夹添加图片列表时，出现 Glide 加载百度云缩略图超时的错误：

```
com.bumptech.glide.load.HttpException: Failed to connect or obtain data, status code: -1
Caused by: java.net.SocketTimeoutException: timeout
Caused by: java.net.SocketException: socket is closed
```

## 根本原因

1. **默认超时时间过短**: Glide 默认使用 Android 系统的 `HttpURLConnection`，其默认超时时间较短（通常为10秒左右）
2. **百度云缩略图加载慢**: 百度云的缩略图 API 可能由于网络延迟或服务器响应较慢，导致连接超时
3. **没有重试机制**: 默认配置在连接失败时不会自动重试

## 解决方案

### 1. 添加 Glide OkHttp3 集成库

在 `app/build.gradle` 中添加依赖：

```gradle
// Image loading
implementation 'com.github.bumptech.glide:glide:4.15.1'
implementation 'com.github.bumptech.glide:okhttp3-integration:4.15.1'
annotationProcessor 'com.github.bumptech.glide:compiler:4.15.1'
```

### 2. 创建自定义 Glide 配置模块

创建 `GlideConfiguration.java` 来配置更长的超时时间和重试机制：

```java
@GlideModule
public class GlideConfiguration extends AppGlideModule {
    @Override
    public void registerComponents(@NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
        // 创建自定义的 OkHttpClient，配置更长的超时时间
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)  // 连接超时30秒
                .readTimeout(30, TimeUnit.SECONDS)     // 读取超时30秒
                .writeTimeout(30, TimeUnit.SECONDS)    // 写入超时30秒
                .retryOnConnectionFailure(true)        // 连接失败时重试
                .addInterceptor(loggingInterceptor)    // 添加日志拦截器
                .build();

        // 替换 Glide 的网络组件
        registry.replace(GlideUrl.class, InputStream.class, 
                new OkHttpUrlLoader.Factory(client));
    }
}
```

### 3. 在 FileAdapter 中设置超时

在 `FileAdapter.java` 的图片加载代码中添加 `timeout()` 方法：

```java
Glide.with(itemView.getContext())
        .load(file.getThumbs().getUrl1())
        .placeholder(android.R.drawable.ic_menu_gallery)
        .error(android.R.drawable.ic_menu_gallery)
        .timeout(30000)  // 设置30秒超时
        .into(ivFileIcon);
```

## 修复效果

1. **延长超时时间**: 从默认的约10秒延长到30秒，给慢速网络更多时间加载
2. **自动重试**: 连接失败时自动重试，提高成功率
3. **更好的日志**: 添加 HTTP 日志拦截器，便于调试网络问题
4. **优雅降级**: 如果仍然加载失败，会显示默认图标，不影响用户操作

## 网络权限确认

确保 `AndroidManifest.xml` 中已配置必要权限：

```xml
<!-- 网络权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<application
    ...
    android:usesCleartextTraffic="true">
```

## 注意事项

1. **Glide 注解处理器**: 添加了 `annotationProcessor` 依赖后，需要重新构建项目以生成 Glide API
2. **网络环境**: 如果用户网络环境确实很差，即使延长超时也可能失败，此时会显示占位图标
3. **性能影响**: 30秒的超时对于图片列表加载来说是合理的，不会明显影响用户体验

## 测试建议

1. 在不同网络环境下测试（WiFi、4G、弱网）
2. 测试大量图片文件夹的加载情况
3. 检查 Logcat 中的 "GlideConfiguration" 和 "OkHttp" 日志
4. 验证加载失败时是否正确显示占位图标

## 相关文件

- `app/build.gradle` - 添加 Glide 依赖
- `app/src/main/java/com/baidu/tv/player/utils/GlideConfiguration.java` - Glide 配置模块
- `app/src/main/java/com/baidu/tv/player/ui/filebrowser/FileAdapter.java` - 文件适配器
- `app/src/main/AndroidManifest.xml` - 网络权限配置