# 媒体文件检测Bug修复

## 问题描述

用户在多选模式下选择"视频"文件夹后，点击"确认选择"时显示"未找到任何媒体文件"错误。

## 根本原因分析

通过代码审查，发现了以下问题：

### 1. MediaType值不一致

**问题：**
- `FileRepository.filterFiles()` 使用的 mediaType 值：
  - 0 = 图片
  - 1 = 视频  
  - 2/3 = 全部
  
- 但 `MediaType.java` 枚举定义的值：
  - 1 = 图片 (IMAGE)
  - 2 = 视频 (VIDEO)
  - 3 = 全部 (ALL)

**影响：**
当传入 mediaType=3 时，旧代码会匹配到 case 3，但实际值错位导致过滤逻辑错误。

### 2. 文件类型检测不够健壮

**问题：**
- `FileInfo.isImage()` 和 `FileInfo.isVideo()` 仅依赖文件扩展名判断
- 百度网盘API提供了 `category` 字段：
  - 1 = 视频
  - 3 = 图片
  - 6 = 文件夹
- 如果扩展名不标准或缺失，无法正确识别文件类型

**影响：**
某些文件即使是有效的媒体文件，也可能因为扩展名问题被过滤掉。

### 3. 调试信息不足

**问题：**
- `fetchFilesRecursive()` 没有详细的调试日志
- 无法追踪递归获取文件的过程和结果

## 修复方案

### 修复1: 统一MediaType值 ([`FileRepository.java:356-378`](app/src/main/java/com/baidu/tv/player/repository/FileRepository.java:356-378))

```java
// 修复前
switch (mediaType) {
    case 0: // 图片
        if (file.isImage()) {
            filteredList.add(file);
        }
        break;
    case 1: // 视频
        if (file.isVideo()) {
            filteredList.add(file);
        }
        break;
    case 2: // 全部（旧值，兼容）
    case 3: // 全部（新值）
        if (file.isImage() || file.isVideo()) {
            filteredList.add(file);
        }
        break;
}

// 修复后
switch (mediaType) {
    case 1: // 图片（与MediaType.IMAGE对应）
        if (file.isImage()) {
            filteredList.add(file);
        }
        break;
    case 2: // 视频（与MediaType.VIDEO对应）
        if (file.isVideo()) {
            filteredList.add(file);
        }
        break;
    case 3: // 全部（与MediaType.ALL对应）
        if (file.isImage() || file.isVideo()) {
            filteredList.add(file);
        }
        break;
    default: // 默认全部
        if (file.isImage() || file.isVideo()) {
            filteredList.add(file);
        }
        break;
}
```

### 修复2: 增强文件类型检测 ([`FileInfo.java:223-253`](app/src/main/java/com/baidu/tv/player/model/FileInfo.java:223-253))

```java
// 修复前：仅依赖扩展名
public boolean isImage() {
    if (serverFilename == null) return false;
    String ext = getExtension().toLowerCase();
    return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") ||
           ext.equals("avif") || ext.equals("webp") || ext.equals("heic") ||
           ext.equals("heif") || ext.equals("bmp") || ext.equals("gif") ||
           ext.equals("tiff") || ext.equals("tif");
}

// 修复后：优先使用category字段
public boolean isImage() {
    // 优先使用百度API的category字段判断
    if (category == 3) {
        return true;
    }
    
    // 如果category不是图片，再通过扩展名判断
    if (serverFilename == null) return false;
    String ext = getExtension().toLowerCase();
    return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") ||
           ext.equals("avif") || ext.equals("webp") || ext.equals("heic") ||
           ext.equals("heif") || ext.equals("bmp") || ext.equals("gif") ||
           ext.equals("tiff") || ext.equals("tif");
}

public boolean isVideo() {
    // 优先使用百度API的category字段判断
    if (category == 1) {
        return true;
    }
    
    // 如果category不是视频，再通过扩展名判断
    if (serverFilename == null) return false;
    String ext = getExtension().toLowerCase();
    return ext.equals("mp4") || ext.equals("mov") || ext.equals("3gp") ||
           ext.equals("mkv") || ext.equals("avi") || ext.equals("m4v") ||
           ext.equals("flv") || ext.equals("wmv") || ext.equals("webm");
}
```

**改进点：**
1. 优先使用百度网盘API的标准`category`字段
2. 保留扩展名检测作为后备方案
3. 增加了更多视频格式支持（flv, wmv, webm）

### 修复3: 添加详细调试日志 ([`FileRepository.java:91-114`](app/src/main/java/com/baidu/tv/player/repository/FileRepository.java:91-114))

```java
public void fetchFilesRecursive(String accessToken, String dirPath, final FileListCallback callback) {
    fetchPagesRecursiveWithLimit(accessToken, dirPath, 0, new ArrayList<>(), 10, new FetchPagesCallback() {
        @Override
        public void onSuccess(List<FileInfo> allFiles, boolean hasMore) {
            // 添加调试日志
            Log.d(TAG, "fetchFilesRecursive完成: 总文件数=" + allFiles.size());
            
            // 打印前5个文件的详细信息
            for (int i = 0; i < Math.min(5, allFiles.size()); i++) {
                FileInfo f = allFiles.get(i);
                Log.d(TAG, "  文件" + i + ": name=" + f.getServerFilename() + 
                    ", isDir=" + f.isDirectory() + 
                    ", category=" + f.getCategory() +
                    ", isImage=" + f.isImage() + 
                    ", isVideo=" + f.isVideo());
            }
            
            callback.onSuccess(allFiles);
        }

        @Override
        public void onFailure(String error) {
            Log.e(TAG, "fetchFilesRecursive失败: " + error);
            callback.onFailure(error);
        }
    });
}
```

## 预期效果

修复后：
1. ✅ MediaType值与枚举定义一致
2. ✅ 文件类型检测更准确（优先使用API的category字段）
3. ✅ 支持更多视频格式
4. ✅ 详细的调试日志便于问题排查
5. ✅ 用户选择"视频"文件夹后能正确识别媒体文件

## 测试建议

1. 在Android Studio中编译并运行应用
2. 点击首页的"创建播放列表"按钮
3. 进入多选模式，选择包含视频/图片的文件夹
4. 点击"确认选择"
5. 查看Logcat日志，确认：
   - `fetchFilesRecursive完成` 日志显示找到文件
   - 文件的 `category` 字段正确
   - `isImage` 和 `isVideo` 判断正确
6. 验证播放列表创建成功，不再显示"未找到任何媒体文件"错误

## 相关文件

- [`FileRepository.java`](app/src/main/java/com/baidu/tv/player/repository/FileRepository.java) - 修复MediaType值，添加调试日志
- [`FileInfo.java`](app/src/main/java/com/baidu/tv/player/model/FileInfo.java) - 增强文件类型检测
- [`FileBrowserFragment.java`](app/src/main/java/com/baidu/tv/player/ui/filebrowser/FileBrowserFragment.java) - 已有详细调试日志

## 百度网盘API Category字段参考

根据百度网盘API文档，category字段的值：
- 1 = 视频
- 2 = 音频
- 3 = 图片
- 4 = 文档
- 5 = 应用
- 6 = 其他
- 7 = 种子

我们的应用主要关注：
- category = 1 (视频)
- category = 3 (图片)