## Why

当前 `pan-tv-player`（Java 版）是针对 Android TV 的百度网盘媒体播放器，已完成功能验证（登录、浏览、播放列表、双引擎播放、GPS 地点识别、9 种图片特效等）。但该版本基于 Java 8 + ExoPlayer 2.18（已 EOL）+ VLC 兜底，在 Amlogic S905X 等 Android 9 低端电视芯片上出现 VLC 硬解花屏、软解卡顿等不可修复的硬件兼容性问题，且多项依赖（AGP 7.4.2、compileSdk 33、targetSdk 33）已违反 Google Play 上架要求。采用 Kotlin 重写并统一播放器栈（Media3 + FFmpeg），可同时解决合规、性能和可维护性三个层面的问题。

## What Changes

- **语言迁移**：Java 8 → Kotlin 2.x，预计代码量减少 40-50%
- **构建升级**：AGP 8.x + Kotlin DSL (`build.gradle.kts`) + KSP（替代 kapt）
- **播放器栈**：移除 ExoPlayer 2.18 和 VLC，统一为 **Media3 (androidx.media3) + FFmpeg 软解码器**扩展
- **并发模型**：LiveData + Thread/Handler → **Kotlin Coroutines + Flow**（LiveData 仅保留 View 层生命周期绑定）
- **依赖注入**：手动单例 → **Hilt**
- **序列化**：手写 Parcelable → `@Parcelize`（kotlin-parcelize 插件）
- **认证安全**：Token SharedPreferences 明文存储 → EncryptedSharedPreferences
- **Room**：annotationProcessor → KSP；`fallbackToDestructiveMigration()` → 正常 Migration 策略
- **UI**：保留 ViewBinding + XML（Compose for TV 目前仍为 beta，不采用）
- **移除依赖**：`mp4parser`（10 年未更新，可用 ExifInterface/MediaMetadataRetriever 替代）、`libvlc-all`（~40MB，花屏根源）
- **最低 SDK**：保持 API 28（Android 9），targetSdk 升级至 34+
- **测试**：从零测试（Java版无任何测试）→ 100% 单测覆盖 ViewModel + Repository + 工具类 + 策略模式；集成测试覆盖 Room DAO + Retrofit API + 播放器降级链；UI 测试覆盖遥控器焦点导航

## Capabilities

### New Capabilities

- `media3-playback`: Media3 为主力播放器（硬解），FFmpeg 扩展为软解兜底；播放前检测 HEVC 10-bit/4K 等超出设备能力的编码参数并友好降级
- `kotlin-coroutines-concurrency`: 协程 + Flow 管理异步操作（文件列表加载、dlink 预取、GPS 提取），替代 Thread + Handler + Thread.sleep 轮询
- `hilt-di`: Hilt 依赖注入替代所有手动单例（BaiduAuthService、FileRepository、RetrofitClient 等）
- `encrypted-auth-storage`: EncryptedSharedPreferences 存储 OAuth token，替代明文 SharedPreferences
- `r8-optimization`: R8 全模式代码混淆 + 资源压缩，确保 APK 体积 ≤ 原 Java 版（无 VLC 后预期缩减 40MB+）
- `testing-strategy`: 严格的三层测试体系（单测/集成测试/UI 测试），TDD 先行，CI 门禁，目标覆盖率 ≥ 80%

### Modified Capabilities

_无 — 此为全新项目，不修改已有 spec。_

## Impact

- **代码**：全项目重写（~30,000 行 Java），架构保持 MVVM + Repository 分层
- **依赖**：移除 `org.videolan.libvlc`、`com.google.android.exoplayer`、`com.googlecode.mp4parser`；新增 `androidx.media3:*`、`com.google.dagger:hilt-*`、KSP
- **数据库**：Room schema 不变（Playlist、PlaylistItem、PlaybackHistory），需写 v1→v2 Migration
- **构建**：`.kts` 脚本替换 `.gradle`；`agp` 7.4.2 → 8.x；JDK 17+
- **测试**：新增 `test/` + `androidTest/` 源码集，引入 MockK + Turbine + Robolectric + Hilt Testing + Compose testing（RecyclerView）库
- **CI**：新增 GitHub Actions / Jenkins 流水线，PR 门禁：`./gradlew test` + `./gradlew connectedCheck` + 覆盖率报告
- **用户数据**：SharedPreferences 迁移到 EncryptedSharedPreferences 需一次性数据搬迁
