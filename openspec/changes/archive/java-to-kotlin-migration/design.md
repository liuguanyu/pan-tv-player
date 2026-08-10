## Context

当前 `pan-tv-player`（Java 版）是一个功能完整的 Android TV 百度网盘播放器，基于 Java 8 + MVVM + LiveData + Retrofit + Room + ExoPlayer 2.18 + VLC 3.5.1。该版本在 Amlogic S905X 等 Android 9 低端电视芯片上存在 VLC 硬解花屏、软解卡顿的硬件兼容性问题，且 `compileSdk`/`targetSdk` 33 已违反 Google Play 上架要求。

本项目为 Kotlin 重写，目标是保持所有现有功能的同时，统一播放器栈（Media3 + FFmpeg）、升级构建链、用 Kotlin 协程和 Hilt 解决当前项目中存在的并发和生命周期管理问题。

## Goals / Non-Goals

**Goals:**
- 1:1 功能对等：所有 Java 版功能（登录、浏览、播放列表、双引擎播放、GPS 地点识别、9 种图片特效、3 种背景模式、遥控器操作）在 Kotlin 版中完整保留
- 统一播放器栈：Media3 + FFmpeg 替代 ExoPlayer 2.18 + VLC，消灭花屏问题
- 构建合规：compileSdk 34+、targetSdk 34+、AGP 8.x、JDK 17+
- 并发安全：协程 + Flow 替代 Thread/Handler 轮询
- DI 解耦：Hilt 替代手动单例
- Token 安全：EncryptedSharedPreferences 替代明文存储
- 测试覆盖：三层测试体系（单测/集成测试/UI 测试），行覆盖率 ≥ 80%，CI 门禁
- APK 体积缩减：移除 VLC ~40MB，FFmpeg 按需 ~5MB，净减 ~35MB

**Non-Goals:**
- 不引入 Jetpack Compose（Compose for TV 仍为 beta）
- 不修改 Room 数据库 schema（保持 v1 结构，仅加 Migration 能力）
- 不新增用户可见功能（这是迁移，不是功能迭代）
- 不改变百度 OAuth 流程逻辑
- 不支持 Android 9 以下设备（minSdk 保持 28）

## Decisions

### 1. 架构：保持 MVVM + Repository，升级为 Kotlin 惯用形式

**决定**：保持三层架构（View → ViewModel → Repository），但 Repository 方法改为 `suspend` 函数，ViewModel 通过 `StateFlow` 向 View 暴露状态。

**替代方案**：MVI（Model-View-Intent）— 拒绝原因：团队对 MVVM 已熟悉，且 MVI 增加的 Reducer/Action 抽象层对当前项目规模来说过度设计。

**包结构**（与 Java 版对应，但包名改为 `com.baidu.tv.player.kt`）：

```
app/src/main/java/com/baidu/tv/player/kt/
├── auth/              # Hilt Module + BaiduAuthService (suspend)
├── di/                # Hilt Modules (NetworkModule, DatabaseModule, AuthModule)
├── model/             # data class (@Parcelize) + enum
├── database/          # Room Entity + DAO (KSP 生成)
├── network/           # Retrofit interfaces + RetrofitClient (Hilt提供)
├── effect/            # ImageEffectStrategy sealed interface
├── background/        # ImageBackgroundStrategy sealed interface
├── geocoding/         # GeocodingStrategy sealed interface + Factory
├── repository/        # FileRepository, PlaylistRepository, PlaybackHistoryRepository (suspend)
├── service/           # LocationExtractionService (保留独立进程)
├── ui/
│   ├── login/         # LoginActivity + LoginViewModel
│   ├── main/          # MainActivity + MainFragment + MainViewModel
│   ├── filebrowser/   # FileBrowserActivity + FileBrowserFragment + FileBrowserViewModel
│   ├── playback/      # PlaybackActivity + PlaybackViewModel + VideoPlayerEngine
│   └── settings/      # SettingsActivity + SettingsViewModel
└── util/              # 扩展函数（String.extractLocation, FileInfo.isPlayable 等）
```

### 2. 播放器：Media3 + FFmpeg 统一栈

**决定**：`androidx.media3:media3-exoplayer` + `androidx.media3:media3-ffmpeg-decoder`。定义 `VideoPlayerEngine` 接口，`Media3VideoPlayerEngine` 为唯一实现。

**替代方案**：Media3 + VLC 双引擎 — 拒绝原因：VLC 的 `--avcodec-hw=any` 在 Amlogic 芯片上硬解花屏，软解效率与 FFmpeg 基本一致，且 VLC 包体积 ~40MB 过大。

**FFmpeg ABI 处理**：仅保留 `arm64-v8a`（Android TV 设备标准架构），通过 `ndk { abiFilters "arm64-v8a" }` 限制。模拟器开发用 `x86_64` 仅在 debug flavor 中包含。

**降级链**：
```
Media3 硬解 (MediaCodec) → 失败 → FFmpeg 软解 → 仍失败 → 编码检测预检
```

**实现期发现（2026-07-11）**：`androidx.media3:media3-ffmpeg-decoder` 在 Maven 上**不存在**——官方 `androidx.media3` group 不发布 FFmpeg 扩展预构建产物（源码模块名实为 `media3-decoder-ffmpeg`，需用 NDK 自行编译 native FFmpeg 库，且涉及 GPL 许可考量）。经评估社区预构建产物后，当前实现采用：

- **Media3 硬解（MediaCodec）为主力**，覆盖 H.264 / HEVC 8-bit 等设备支持编码。
- **播放前编码参数检测**（`MediaMetadataRetriever`）已在设计中，对 4K HEVC / 10-bit HEVC 弹出警告对话框（仍然播放 / 跳过），作为"硬解不可用时"的用户可见兜底。
- **FFmpeg 软解**作为 `VideoPlayerEngine` 抽象内的**预留插入点**保留：`PlaybackResult.Unsupported(codec)` 分支已建模，后续若引入自编译或可信社区 FFmpeg 扩展，只需在 `Media3VideoPlayerEngine` 降级链中接入，无需改动调用方。
- 此不阻塞主目标（移除 VLC = 消除花屏 + 缩减 ~40MB）。

> 影响 tasks：6.3（FFmpeg 软解降级链标记为预留）、8.3（`abiFilters "arm64-v8a"` 仍保留用于未来 FFmpeg native）。spec `media3-playback` 的"FFmpeg 软解码器扩展作为兜底" Requirement 当前以"架构预留 + 编码检测兜底"满足，待 FFmpeg 产物可用后补齐软解路径。
### 3. 依赖注入：Hilt

**决定**：`com.google.dagger:hilt-android` + `hilt-android-compiler`（KSP 处理器）。

**模块设计**：
- `NetworkModule`：`@Provides @Singleton @Qualifier("panApi")` Retrofit + `@Qualifier("oauth")` Retrofit + OkHttpClient
- `DatabaseModule`：`@Provides @Singleton` AppDatabase + PlaylistDao + PlaybackHistoryDao + PlaylistItemDao
- `AuthModule`：`@Provides @Singleton` BaiduAuthService + EncryptedSharedPreferences

**替代方案**：Koin — 拒绝原因：运行时 DI 无编译期校验，大型项目维护成本高；Hilt 是 Google 官方推荐，与 ViewModel/Jetpack 集成最紧密。

### 4. 并发：协程 + Flow + LiveData 桥接

**决定**：
- Repository 方法：`suspend` 函数（`suspendCancellableCoroutine` 包装 Retrofit Callback）
- ViewModel → Repository：`viewModelScope.launch { }`
- ViewModel → View：`StateFlow` 通过 `stateIn()` 保持热流，View 层用 `LiveData` 桥接（`liveData { }` 构建器）或直接 `repeatOnLifecycle` 收集 Flow
- 不引入 RxJava（避免混合两种并发模型）

**替代方案**：全 Flow 不保留 LiveData — 部分可行，但 FileBrowserFragment 的 `RecyclerView` 适配器更新依赖 LiveData 的 `observe(viewLifecycleOwner)` 生命周期感知，改为 Flow 需额外处理 `repeatOnLifecycle`，收益不大。

### 5. 构建系统：Kotlin DSL + AGP 8.x + KSP

**决定**：
- `settings.gradle.kts` + `build.gradle.kts`（根 + app 模块）
- AGP 8.5.x，compileSdk 35，targetSdk 35，minSdk 28
- KSP 替代 kapt（Room、Hilt、Glide 注解处理）
- `kotlin-parcelize` 插件替代手写 Parcelable
- `com.google.devtools.ksp` 插件

**版本锁定**（catalog 或显式声明）：
```
kotlin = "2.0.21"
agp = "8.7.3"
ksp = "2.0.21-1.0.28"
media3 = "1.4.1"
room = "2.6.1"
hilt = "2.51.1"
retrofit = "2.11.0"
glide = "4.16.0"
```

### 6. Token 安全：EncryptedSharedPreferences

**决定**：`androidx.security:security-crypto:1.1.0-alpha06`。`BaiduAuthService` 的 `saveAuthInfo()`/`loadAuthInfo()` 通过 `EncryptedSharedPreferences` 读写。应用首次启动时检测旧 `baidu_auth` SharedPreferences，执行一次性迁移后删除旧文件。

**替代方案**：DataStore — 拒绝原因：DataStore 虽推荐但无内置加密，需额外引入 `EncryptedDataStore` 或手动加密，复杂度高；`EncryptedSharedPreferences` 是 AndroidX 官方加密方案，API 与 SharedPreferences 一致，迁移成本最低。

### 7. 图片特效与背景：策略模式保持不变

**决定**：保留 Java 版的策略模式设计，但改为 Kotlin 表达：
- `ImageEffectStrategy` → `sealed interface` 或 `fun interface`（如果只有 apply 方法）
- `ImageBackgroundStrategy` → 同上
- `GeocodingStrategy` → 同上
- 工厂方法用 `companion object` 替代静态方法

### 10. 百度网盘接入方式：不依赖官方 Android SDK

**决定**：继续使用手写 Retrofit API（设备码 OAuth + PCS 文件 API），**不接入百度网盘官方 Android SDK**。

**调研过程**（2025-07-10）：

对百度网盘开放平台 Android SDK（`baidu-oauth-sdk-android-release-2.0.7.zip`，AAR 手动分发）进行了全面评估：

| 评估维度 | 结论 | 详情 |
|---|---|---|
| **分发方式** | ❌ 不兼容现代构建 | AAR 手动下载，非 Maven；包名+MD5 签名绑定，每次签名变更需重新发邮件申请（6-10 工作日） |
| **授权方式** | ❌ 不兼容 TV | SDK 仅支持 SSO（需要百度系 App）和 WebView（TV 体验极差），**不提供设备码（device_code）授权 API** |
| **minSdk** | ⚠️ 未标注 | 文档未声明最低 SDK，存在兼容性风险 |
| **文件操作模块** | ✅ 功能完整 | `getFileList`/`getFileMeta`/`searchFile` 等可替代手写 API，但需先通过授权拿到 token 后手动设置 |
| **音视频内核模块** | ⚠️ 能力未知 | 提供 `previewVideo(data, listener)` + `getResolution()` 接口，但文档未说明解码器实现、HEVC 支持、是否使用服务端转码 |

**核心拒绝原因**：SDK 的授权模块（SSO + WebView）完全不适用于 Android TV 设备。设备码模式（屏幕显示二维码 → 手机扫码）是 TV 的唯一可行方案，但 SDK 不支持。如果混用（授权手写 + 文件/播放走 SDK），会导致两套认证管线并存的维护负担，且 AAR 手动集成破坏 Gradle 版本管理的一致性。

**替代方案识别**：

1. **媒体点播服务**（商务合作）：百度提供 "支持种类多、多种清晰度" 的音视频点播服务，可能是**服务端转码**（HEVC → H.264），直接从根源解决 Android 9 低端芯片播放问题。但这是商务合作接口（需邮件联系 `ext_mars-union@baidu.com`），非开放 API，成本和时间不可控。建议：Kotlin 版首次发布后，视用户反馈决定是否联系百度商务团队。

2. **播单能力 API**：开放平台提供 HTTP API 的播单能力（获取播单列表、播单下音频播放、播单下文件下载），但同样不支持视频播放的转码参数。

**影响**：维持原有架构不变 — Retrofit 手写 PCS API + Media3 + FFmpeg。不引入额外依赖和认证管线。

### 9. 测试策略：三层测试 + TDD

**决定**：

| 层级 | 工具栈 | 目标 |
|---|---|---|
| **单元测试** (`test/`) | JUnit5 + MockK + Turbine + kotlinx-coroutines-test | ViewModel / Repository / 工具类 / 策略模式，行覆盖率 ≥ 85% |
| **集成测试** (`androidTest/`) | Robolectric + Hilt Testing + MockWebServer + Room in-memory | Room DAO / Retrofit API / 播放器降级链 / Hilt DI 图 |
| **UI 测试** (`androidTest/`) | Espresso + UIAutomator（RecyclerView + D-pad 焦点） | 主界面焦点链 / 文件浏览焦点 / 播放页控制栏（≤ 15% 总量） |

**测试基础设施**：
- `HiltTestRunner`：自定义 `AndroidJUnitRunner`，Hilt 注入 TestDatabaseModule（内存数据库）替代真实 DatabaseModule
- `FakeFileRepository` / `FakeBaiduPanService`：可注入的 Fake，用于 ViewModel 单测隔离网络
- `MainCoroutineRule`：`TestScope` + `TestDispatcher` JUnit Rule，替换 `viewModelScope` 的 Dispatcher
- CI：GitHub Actions 执行 `./gradlew test` + `androidTest`（api 28 TV 模拟器），JaCoCo 生成覆盖率报告

**TDD 执行规则**：每个 `tasks.md` 实现任务必须以对应测试任务为依赖 — "先红再绿"。实现文件与测试文件同时提交，不允许先提交实现后补测试。

**Non-Goals（测试不覆盖的）**：
- Glide 图片加载（框架行为，无业务逻辑）
- Media3/FFmpeg 内部解码细节（框架集成测试已覆盖接口）
- 百度真实 API 端点（MockWebServer 模拟即可，不做端到端）

### 11. 地理位置提取改进：基于 Java 版痛点优化

**决定**：针对 Java 版「iPhone 12+ 媒体文件 GPS 提取经常失败」的问题，做以下关键改进。

**问题诊断（Java 版痛点）**：

1. **多余的 `ACCESS_FINE_LOCATION` 权限检查阻断了整个提取流程**（最高优先级）。`ExifInterface.getLatLong()`、`MediaMetadataRetriever`、`Geocoder.getFromLocation()` 这三个 API **全部不需要 `ACCESS_FINE_LOCATION` 权限**——该权限只用于 `LocationManager` 获取设备当前位置，与从文件元数据中读取已有 GPS 完全无关。Android TV 无 GPS 硬件，用户拒绝此权限后 `getLocationForFile()` 直接 `return`，GPS 提取从未启动。

2. **图片下载全量 10MB + 硬截断**：完整下载图片再读 EXIF，10MB 硬截断有拿到不完整文件的风险。EXIF 数据在文件头前 64KB 内（JPEG APP1 marker / HEIC iloc box），完全不需要下载全图。

3. **视频「头尾各 2MB 文本搜 GPS」是无效 hack**：三路并行（`MediaMetadataRetriever` + 文件头 2MB 文本搜 + 文件尾 2MB 文本搜）。后两路用 `ISO-8859-1` 解码 2MB 二进制数据，再用 8 种正则搜 GPS 字符串——对 iPhone 视频的 `©xyz` QuickTime atom 几乎必然失败，徒增复杂度和假阳性风险。`MediaMetadataRetriever` 是唯一能正确读取 QuickTime `©xyz` GPS 的 API。

4. **无超时机制**：`MediaMetadataRetriever.setDataSource(url, headers)` 对百度网盘 CDN 重定向链可能无限阻塞。

**改进方案**：

1. **删除 `ACCESS_FINE_LOCATION` 权限检查**
   - Kotlin 版 `getLocationForFile()` 不做任何位置权限检查
   - `AndroidManifest.xml` 移除 `ACCESS_FINE_LOCATION` 和 `ACCESS_COARSE_LOCATION` 声明
   - 理由：这些权限与读取文件 EXIF/元数据无关

2. **图片：Range 请求只下载文件头 128KB 读 EXIF**
   - 用 HTTP `Range: bytes=0-131071` 只下载文件头，不下全图
   - 原来：10MB 全量下载 / 10s+ → 改进后：128KB / <1s
   - 移除 10MB 硬截断逻辑，避免 `break` 后拿到不完整文件
   - 临时文件后缀根据 `Content-Type` 推断（`.heic`/`.jpg`），不使用 `.tmp`

3. **视频：放弃头尾文本搜，只保留 `MediaMetadataRetriever` + 超时**
   - **删除**「文件头 2MB 文本搜」和「文件尾 2MB 文本搜」逻辑（无用的 hack）
   - 只保留 `MediaMetadataRetriever`（读 QuickTime `©xyz` GPS 的唯一正确 API）
   - 加 `withTimeout(8000)` 包装，防止无限阻塞

4. **拿不到 GPS 就静默跳过**
   - UI 层：`location != null` → 显示，`location == null` → 什么都不显示
   - **不显示**「提取失败」或任何提示——用户不需要感知这个内部细节

**影响**：
- `AndroidManifest.xml`：移除 2 个 `uses-permission`（即使用户授予也毫无作用）
- `LocationExtractionService`：移除权限检查逻辑，添加协程 `withTimeout` 包装
- `LocationUtils`：图片 → Range 下载 128KB；视频 → 精简为 `MediaMetadataRetriever` + 超时
- 预期效果：iPhone 12+ 媒体文件 GPS 提取成功率大幅提升（主要收益来自 ①解除权限阻断；②图片 Range 下载避免截断）

## Risks / Trade-offs

| Risk | Impact | Mitigation |
|---|---|---|
| FFmpeg 软解 1080p HEVC 10-bit 在 S905X 上依然卡顿 | 用户无法播放高质量 HEVC | 播放前编码参数检测 → 弹出警告 → 用户可选择跳过；后续可考虑服务端转码 |
| EncryptedSharedPreferences 在个别设备上 Keystore 初始化失败 | 用户无法登录 | 捕获 `KeyStoreException` → 降级到明文 SharedPreferences + 日志告警（不与 spec 冲突，属于防御性降级） |
| Hilt 编译时间增加 | 首次全量编译 +30-60s | KSP 替代 kapt 可回收大部分时间；增量编译影响小 |
| Kotlin 2.x 与 AGP 8.x 兼容性 | 特定版本组合可能报错 | 使用 Google 官方推荐的版本映射表，锁定稳定组合 |
| 测试编写耗时 | 首次全量覆盖 80% 需要额外 30-50% 开发时间 | 这是预期成本，不是风险 — TDD 在项目后期节省回归和重构成本 |
| 现有 Java 版私有 API 调用（如 `SystemProperties`）在 Kotlin 中可能需额外处理 | 编译错误 | 通过 `@Suppress("DEPRECATION")` + 反射兜底 |

## Migration Plan

1. **Phase 0**：环境搭建 — AGP 8.x + Kotlin DSL + KSP 空项目编译通过
2. **Phase 1**：数据层 — model (data class) + database (Room) + network (Retrofit) + repository (suspend)
3. **Phase 2**：认证 — Hilt Modules + BaiduAuthService + EncryptedSharedPreferences + LoginActivity
4. **Phase 3**：主界面 — MainFragment + 播放列表 + 最近任务
5. **Phase 4**：文件浏览 — FileBrowserFragment + 递归扫描 + 多选模式
6. **Phase 5**：播放器 — Media3 + FFmpeg + VideoPlayerEngine + 编码检测 + 图片特效 + 背景
7. **Phase 6**：设置 — SettingsActivity + 地点识别 + 遥控器
8. **Phase 7**：优化 — R8 规则 + ProGuard 测试 + APK 体积验证

**回滚策略**：不涉及回滚（新项目，非原地迁移）。Java 版继续独立维护，直到 Kotlin 版功能对等且稳定。

## Open Questions

1. **百度网盘 API 是否支持 H.264 转码流？** 如果百度网盘 `dlink` 返回时可选择转码参数，可在服务端解决 HEVC 兼容性问题，客户端无需软解。待调研百度 PCS API 文档。
2. **是否需要 `libvlc` 保留作为极端格式兜底？** 当前决定移除，但如果在实际测试中发现 Media3 + FFmpeg 无法覆盖某些古老的百度网盘转码格式（如 `.asf`、`.wmv`），可考虑在 PlaybackEngine 中留一个 `VlcEngine` 实现但默认不编译，通过 flavor 控制。
3. **LocationExtractionService 的独立进程在 Kotlin 中是否保留？** 保留 — 独立进程防 native 崩溃的前提不变。Kotlin 中 `IntentService` 已废弃，需迁移到 `Service` + 协程。同时修复 Java 版的两大痛点：①删除多余的 `ACCESS_FINE_LOCATION` 权限检查（曾阻断整个提取流程）；②图片改用 Range 请求只下 128KB 文件头读 EXIF，不下载全图。