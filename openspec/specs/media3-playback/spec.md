## Requirements

### Requirement: Media3 作为主力播放器
系统 SHALL 使用 `androidx.media3:media3-exoplayer` 作为默认视频播放引擎。

#### Scenario: 正常播放 H.264 视频
- **WHEN** 用户播放 H.264/AAC 编码的 MP4 视频
- **THEN** 系统通过 Media3 硬解播放，不消耗 CPU 软解资源，且画面无花屏/撕裂

#### Scenario: 硬解 HEVC 失败不回退到有 bug 的 vendor 实现
- **WHEN** Media3 尝试硬解 HEVC 视频但设备 MediaCodec 返回错误
- **THEN** 系统 SHALL NOT 尝试绕过 Media3 直接调用 MediaCodec，而是标记为"硬解不可用"并触发软解回退

### Requirement: FFmpeg 软解码器扩展作为兜底
系统 SHALL 集成 `androidx.media3:media3-ffmpeg-decoder` 扩展模块，在硬解失败时自动回退到 FFmpeg HEVC 软解码。

#### Scenario: HEVC 8-bit 硬解失败后流畅软解
- **WHEN** 播放 1080p HEVC 8-bit 24fps 视频且硬解不可用
- **THEN** FFmpeg 软解接管，解码帧率 ≥ 22fps，音画同步误差 ≤ 200ms

#### Scenario: HEVC 10-bit 软解性能不足时友好提示
- **WHEN** 播放 HEVC 10-bit 视频且实际解码帧率低于 18fps 持续 5 秒以上
- **THEN** 系统 SHALL 显示 Toast 提示"HEVC 10-bit 编码超出设备解码能力"，并询问是否继续或跳过

### Requirement: 播放前编码参数检测
系统 SHALL 在开始播放前通过 `MediaMetadataRetriever` 或 `DefaultExtractorsFactory` 获取视频的编码格式 (`mime`)、分辨率 (`width`/`height`)、帧率，并根据设备能力分级。

#### Scenario: 检测到 4K HEVC 播放前阻止
- **WHEN** 视频编码为 HEVC 且宽度 ≥ 3840
- **THEN** 系统 SHALL 在播放前弹出对话框："此视频为 4K HEVC 编码，您的设备可能无法流畅播放"，提供"仍然播放"和"跳过"两个选项

#### Scenario: 检测到 HEVC 10-bit 播放前警告
- **WHEN** 视频编码为 HEVC 且 bit depth = 10
- **THEN** 系统 SHALL 在播放前弹出警告对话框，建议用户跳过

#### Scenario: H.264 视频不触发检测警告
- **WHEN** 视频编码为 H.264/AVC
- **THEN** 系统 SHALL 直接播放，不弹出任何编码检测提示

### Requirement: 移除 VLC 依赖
系统 SHALL NOT 依赖 `org.videolan.libvlc` 或任何 VLC 组件。

#### Scenario: APK 不包含 VLC 原生库
- **WHEN** 构建 Release APK
- **THEN** APK 内不包含 libvlc.so、libvlcjni.so 等 VLC 相关 .so 文件，且 APK 体积比 Java 版减少 ≥ 35MB

### Requirement: 播放器引擎抽象
系统 SHALL 定义 `VideoPlayerEngine` 接口，支持通过依赖注入切换播放器实现（当前仅有 Media3 实现，但架构预留扩展点）。

#### Scenario: 播放器引擎可注入
- **WHEN** `PlaybackViewModel` 需要播放视频
- **THEN** 通过 Hilt 获取 `VideoPlayerEngine` 实例，ViewModel 不直接依赖 Media3 API

#### Scenario: 播放结果密封类建模
- **WHEN** 播放结束或出错
- **THEN** 返回 `PlaybackResult` sealed class（`Success` / `Unsupported(codec)` / `Error(cause)`），调用方通过 `when` 穷举处理
