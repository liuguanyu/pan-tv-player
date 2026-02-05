# 视频播放问题修复总结

## 修复日期
2026-02-04

## 问题描述
1. **进度条不更新**：播放视频时进度条有时不更新
2. **有声音无图像**：在模拟器上偶尔出现音频正常但视频不显示的问题

## 修复内容

### 1. 进度条更新修复

#### 修改文件：`PlaybackActivity.java`

**问题原因**：
- 在 `playVideoWithUrl()` 方法中，播放视频后没有调用 `startProgressUpdate()` 启动进度更新

**修复方案**：
- 在 ExoPlayer 播放时添加 `startProgressUpdate()` 调用
- 在 VLC 播放时添加 `startProgressUpdate()` 调用

**代码位置**：
- 第 901 行：ExoPlayer 播放后启动进度更新
- 第 886 行：VLC 播放后启动进度更新

### 2. 视频渲染监控增强

#### 修改文件：`PlaybackActivity.java`

**问题原因**：
- 缺少对视频轨道和渲染状态的监控
- 无法诊断"有声音无图像"的根本原因

**修复方案**：
- 在 `onPlaybackStateChanged()` 的 `STATE_READY` 状态下添加视频轨道检测
- 添加 `onRenderedFirstFrame()` 回调来监控第一帧渲染
- 添加详细的日志输出，便于诊断问题

**新增功能**：
```java
// 检测视频和音频轨道
Tracks tracks = exoPlayer.getCurrentTracks();
boolean hasVideo = false;
boolean hasAudio = false;

// 遍历轨道组
for (Tracks.Group trackGroup : tracks.getGroups()) {
    if (trackGroup.getType() == C.TRACK_TYPE_VIDEO && trackGroup.isSelected()) {
        hasVideo = true;
        Log.d("PlaybackActivity", "✓ 检测到视频轨道");
    }
    if (trackGroup.getType() == C.TRACK_TYPE_AUDIO && trackGroup.isSelected()) {
        hasAudio = true;
        Log.d("PlaybackActivity", "✓ 检测到音频轨道");
    }
}

// 警告：有音频但没有视频
if (hasAudio && !hasVideo) {
    Log.w("PlaybackActivity", "⚠️ 警告：检测到音频但没有视频轨道");
}

// 监听第一帧渲染
@Override
public void onRenderedFirstFrame() {
    Log.d("PlaybackActivity", "✓ ExoPlayer 渲染了第一帧视频");
}
```

### 3. PlayerView 配置优化

#### 修改文件：`activity_playback.xml`

**问题原因**：
- `resize_mode="fill"` 可能导致视频变形
- 缺少缓冲状态显示

**修复方案**：
- 将 `resize_mode` 从 `fill` 改为 `fit`，保持视频比例
- 添加 `app:show_buffering="when_playing"` 显示缓冲状态
- 更新注释，将 ExoPlayer 标记为"主力播放器"

**修改内容**：
```xml
<!-- 修改前 -->
app:resize_mode="fill"

<!-- 修改后 -->
app:resize_mode="fit"
app:show_buffering="when_playing"
```

### 4. 注释更新

#### 修改文件：`PlaybackActivity.java` 和 `activity_playback.xml`

**修改内容**：
- 将所有 ExoPlayer 相关的注释从"备用"改为"主力播放器"
- 更新类文档，明确播放器策略
- 移除播放界面上的播放器指示器显示，改为只在日志中记录

**示例**：
```java
// 修改前
// ExoPlayer 播放器 (主播放器)
// 默认使用 ExoPlayer (性能优先)，失败时自动切换到 VLC (兼容性补充)

// 修改后
// ExoPlayer 播放器 (主力播放器)
// 默认使用 ExoPlayer (主力播放器)，失败时自动切换到 VLC (备用播放器)
```

## 播放器策略

### 当前策略
1. **主力播放器**：ExoPlayer
   - Google 官方推荐
   - 性能更好
   - 适合 Android TV
   - 支持硬件加速

2. **备用播放器**：VLC
   - 支持更多格式（HEVC/H.265 等）
   - 兼容性更好
   - 在 ExoPlayer 失败时自动切换

3. **错误处理**：
   - ExoPlayer 解码器错误 → 直接切换到 VLC
   - ExoPlayer 其他错误 → 重试 1 次
   - VLC 错误 → 重试 2 次
   - 两者都失败 → 跳过当前文件

4. **播放器指示**：
   - 不再在播放界面上显示播放器标识
   - 通过日志记录当前使用的播放器：
     ```
     PlaybackActivity: 当前使用的播放器: ExoPlayer
     ```

## 测试建议

### 1. 进度条测试
- 播放多个视频文件
- 检查进度条是否正常更新
- 检查时间显示是否正确

### 2. 视频渲染测试
- 在模拟器上测试多个视频
- 检查是否还有"有声音无图像"的问题
- 查看 Logcat 日志，确认：
  - 是否检测到视频轨道
  - 是否渲染了第一帧
  - 视频和音频轨道状态

### 3. 日志监控
关注以下日志：
```
PlaybackActivity: ExoPlayer state changed: READY
PlaybackActivity: ✓ 检测到视频轨道
PlaybackActivity: ✓ 检测到音频轨道
PlaybackActivity: 视频轨道: true, 音频轨道: true
PlaybackActivity: ✓ ExoPlayer 渲染了第一帧视频
PlaybackActivity: 当前使用的播放器: ExoPlayer
```

如果出现警告：
```
PlaybackActivity: ⚠️ 警告：检测到音频但没有视频轨道
```
说明视频文件可能有问题，或者解码器不支持该格式。

## 已知问题

### 模拟器兼容性
- 模拟器上的视频渲染可能不如真机稳定
- 某些视频格式在模拟器上可能无法正常渲染
- 建议在真机（电视盒子）上进行最终测试

### 硬件加速
- ExoPlayer 默认使用硬件加速
- 如果硬件加速失败，会自动回退到软件解码
- 可以通过日志查看解码器使用情况

## 后续优化建议

1. **添加视频格式检测**
   - 在播放前检测视频格式
   - 对于不支持的格式提前提示

2. **改进错误提示**
   - 显示更详细的错误信息
   - 提供用户友好的错误提示

3. **性能监控**
   - 添加播放性能统计
   - 监控缓冲时间和帧率

4. **用户反馈**
   - 收集用户反馈
   - 根据实际情况调整播放器策略