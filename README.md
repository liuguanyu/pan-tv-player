# PanTVPlayer (Kotlin) — 百度网盘 Android TV 播放器

Android TV 端的百度网盘媒体播放器，支持视频/图片播放、播放列表管理、图片特效与背景、媒体 GPS 地点识别。本项目为 Java 版 `pan-tv-player` 的 Kotlin 重写，统一播放器栈（Media3 替代 ExoPlayer 2.18 + VLC），升级构建链与并发模型。

## 技术栈

| 层 | 技术 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| 构建 | AGP 8.9.1 + Gradle Kotlin DSL + KSP 2.0.21-1.0.28 |
| 最低/目标 SDK | minSdk 28 / targetSdk 36 / compileSdk 36 |
| 架构 | MVVM + Repository（View → ViewModel → Repository） |
| 依赖注入 | Hilt 2.51.1（KSP 处理器，替代手动单例） |
| 并发 | Kotlin Coroutines + Flow + LiveData 桥接（替代 Thread/Handler 轮询） |
| 持久化 | Room 2.6.1（KSP 生成，schema v2） |
| 网络 | Retrofit 2.11.0 + OkHttp 4.12.0 + Gson 2.11.0 |
| 播放器 | Media3 1.4.1（ExoPlayer + MediaCodec 硬解） |
| 图片加载 | Glide 4.16.0 + Palette |
| 安全存储 | EncryptedSharedPreferences（AES-256 GCM，token 加密） |
| 二维码 | ZXing 3.5.3 |
| UI | AndroidX Leanback（TV 专用）+ ViewBinding |
| 测试 | JUnit4 + MockK + Turbine + Robolectric + MockWebServer + Room in-memory |

## 功能

- **登录**：设备码 OAuth（屏幕二维码 → 手机扫码），token 加密存储
- **文件浏览**：百度网盘目录树，递归/非递归扫描，多选建播放列表
- **播放列表**：创建/删除/刷新，续播记忆
- **视频播放**：Media3 硬解，播放前编码参数检测（4K HEVC / 10-bit 警告）
- **图片播放**：9 种特效（Fade/Ease/Float/Bounce/Blinds/Zoom/Rotate/Slide/Random）+ 3 种背景（Black/DominantColor/Blur）
- **地点识别**：从媒体 EXIF/元数据提取 GPS → 逆地理编码显示地址（无需位置权限）
- **设置**：播放模式、特效、时长、地点显示等偏好持久化

## 模块结构

```
app/src/main/java/com/baidu/tv/player/kt/
├── BaiduTVApplication.kt      # @HiltAndroidApp，启动迁移
├── auth/                       # BaiduAuthService (suspend) + AuthMigration + LoginViewModel
├── config/                     # BaiduConfig（API key/secret）
├── database/                   # AppDatabase + DAO（KSP 生成）
├── di/                         # Hilt Modules（Network/Database/Auth/Settings/Player/Geocoding/Location）
├── model/                      # data class (@Parcelize) + Room Entity + enum
├── network/                    # BaiduPanService (Retrofit suspend) + ApiConstants
├── player/                     # VideoPlayerEngine 接口 + Media3VideoPlayerEngine + 编码检测
├── repository/                 # FileRepository / PlaylistRepository / PlaybackHistoryRepository / SettingsRepository
├── location/                   # LocationExtractionService + LocationExtractor + Geocoding 策略
│   └── geocoding/              # Amap / AndroidGeocoder / Nominatim 三策略 + Factory
├── ui/
│   ├── login/                  # LoginActivity + LoginViewModel
│   ├── main/                   # MainActivity + MainFragment + MainViewModel + Adapters
│   ├── filebrowser/            # FileBrowserActivity + Fragment + ViewModel + FileAdapter
│   ├── playback/               # PlaybackActivity + ViewModel + 图片特效/背景策略
│   │   └── image/              # ImageEffectStrategy + ImageBackgroundStrategy + BlindsImageView
│   └── settings/               # SettingsActivity + SettingsViewModel
└── util/                       # PlaylistCache + QRCodeUtils
```

## 构建方式

### 环境要求

- JDK 17+（构建用 Android Studio JBR 21）
- Android SDK 36 + Build Tools 36.1.0
- Gradle Wrapper（已包含 `gradlew`）

### 构建

```bash
# Debug APK
./gradlew assembleDebug

# Release APK（R8 full mode + 资源压缩 + 混淆）
./gradlew assembleRelease

# 输出：app/build/outputs/apk/release/app-release.apk
```

### 测试

```bash
# 单元测试（纯 JVM，无需设备）
./gradlew testDebugUnitTest

# 覆盖率报告（JaCoCo）
./gradlew testDebugUnitTest jacocoTestReport
# 报告：app/build/reports/jacoco/jacocoTestReport/html/index.html

# Android 集成测试（需 API 28 TV 模拟器）
./gradlew connectedAndroidTest
```

## APK 优化（Phase 7）

- **R8 full mode**：`android.enableR8.fullMode=true` + `isMinifyEnabled=true` + `isShrinkResources=true`
- **ABI 变体**：
  - `arm64Release`：仅 `arm64-v8a`，适合 64 位 Android TV，体积较小
  - `compatRelease`：`arm64-v8a` + `armeabi-v7a`，用于 Sony BRAVIA 等 Android 9 但可能运行 32 位系统的真机兜底
- **Release 本地签名**：可在 gitignore 的 `local.properties` 中配置 `signing.store.file` / `signing.store.password` / `signing.key.alias` / `signing.key.password`
- **ProGuard keep 规则**：覆盖 Gson model / Room 实体 / Retrofit 接口 / Hilt 生成类 / Media3 / Kotlin Metadata / Coroutines / Glide / EncryptedSharedPreferences / ZXing

### Release APK 构建

`local.properties` 示例：

```properties
signing.store.file=/Users/liuguanyu/devspace/android/pan-tv-player-kt.jks
signing.store.password=你的密钥库密码
signing.key.alias=key0
signing.key.password=你的 key 密码
```

构建常规 64 位包：

```bash
./gradlew assembleArm64Release
```

构建现场兜底兼容包：

```bash
./gradlew assembleCompatRelease
```

输出位置：

```text
app/build/outputs/apk/arm64/release/app-arm64-release.apk
app/build/outputs/apk/compat/release/app-compat-release.apk
```

## 迁移说明（Java → Kotlin）

本项目为 `pan-tv-player`（Java 版）的 Kotlin 重写，关键变更：

1. **播放器统一**：ExoPlayer 2.18 + VLC 3.5.1 → Media3 1.4.1（消除 VLC 在 Amlogic 芯片上的花屏问题，缩减 ~40MB）
2. **并发模型**：Thread/Handler 轮询 → 协程 + Flow（`suspend` 函数 + `viewModelScope`）
3. **依赖注入**：手动单例 → Hilt（编译期校验 DI 图）
4. **Token 安全**：明文 SharedPreferences → EncryptedSharedPreferences（AES-256 GCM）
5. **地点识别改进**：移除多余的 `ACCESS_FINE_LOCATION` 权限检查；图片改用 HTTP Range 下载 128KB 头部读 EXIF；视频放弃头尾文本搜索 hack，仅用 `MediaMetadataRetriever` + `withTimeout`
6. **构建链升级**：AGP 8.x + KSP + Kotlin DSL + compileSdk 36

详见 [`openspec/changes/java-to-kotlin-migration/`](openspec/changes/java-to-kotlin-migration/)。

## 许可证

私有项目，保留所有权利。
